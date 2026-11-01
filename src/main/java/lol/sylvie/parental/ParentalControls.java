package lol.sylvie.parental;

import lol.sylvie.parental.command.ParentalControlsCommand;
import lol.sylvie.parental.config.Configuration;
import lol.sylvie.parental.util.Formatting;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.permissions.Permissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class ParentalControls implements ModInitializer {
    public static final String MOD_ID = "parentalcontrols";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static int dailyMinutesAllowedInTicks;
    private static int maxAccumulableHoursInTicks;
    private static int firstWarningThresholdInTicks;
    private static int secondWarningThresholdInTicks;
    private static int ticksPerCheck;

    public static final HashMap<UUID, Integer> ticksUsedToday = new HashMap<>();
    public static final HashMap<UUID, Integer> accumulatedTicks = new HashMap<>();

    private static final HashSet<UUID> playersWarnedFirst = new HashSet<>();
    private static final HashSet<UUID> playersWarnedSecond = new HashSet<>();
    private static final HashSet<UUID> playersDisconnecting = new HashSet<>();

    private enum WarningStage {
        FIRST,
        SECOND
    }

    /*
     * Short action-bar messages can easily be overwritten by other server mods.
     * Once a player reaches a warning threshold, resend the same overlay on the
     * normal check interval for a short period. Each configured warning stage
     * still triggers only once per day.
     */
    private static final int WARNING_VISIBLE_TICKS = 10 * 20;
    private static final HashMap<UUID, Integer> warningTicksRemaining = new HashMap<>();
    private static final HashMap<UUID, WarningStage> activeWarningStage = new HashMap<>();

    private LocalDateTime lastTickTime = LocalDateTime.now();
    private int tickCounter = 0;

    public static void updateTimeConstants() {
        ticksPerCheck = Math.max(1, Configuration.INSTANCE.checkIntervalTicks);
        firstWarningThresholdInTicks = Math.max(0, Configuration.INSTANCE.warningThresholdSeconds * 20);
        secondWarningThresholdInTicks = Math.max(0, Configuration.INSTANCE.secondWarningThresholdSeconds * 20);

        if (Configuration.INSTANCE.secondWarningEnabled
                && secondWarningThresholdInTicks >= firstWarningThresholdInTicks
                && firstWarningThresholdInTicks > 0) {
            LOGGER.warn("Second warning threshold ("
                    + Configuration.INSTANCE.secondWarningThresholdSeconds
                    + "s) should be lower than first warning threshold ("
                    + Configuration.INSTANCE.warningThresholdSeconds + "s).");
        }
        dailyMinutesAllowedInTicks = Math.max(0, (int) (Configuration.INSTANCE.minutesAllowed * 60 * 20));
        maxAccumulableHoursInTicks = Math.max(0, (int) (Configuration.INSTANCE.maxStackedHours * 60 * 60 * 20));
    }

    public static void loadAccumulatedTicksFromConfig() {
        accumulatedTicks.clear();
        accumulatedTicks.putAll(Configuration.INSTANCE.playerAccumulatedTicks);
    }

    public static int ticksRemaining(UUID player) {
        int usedToday = ticksUsedToday.getOrDefault(player, 0);
        int accumulated = Configuration.INSTANCE.allowTimeStacking
                ? accumulatedTicks.getOrDefault(player, 0)
                : 0;

        int remainingDaily = Math.max(0, dailyMinutesAllowedInTicks - usedToday);
        return remainingDaily + accumulated;
    }

    private static void consumeTime(UUID playerId, int ticksToConsume) {
        int usedToday = ticksUsedToday.getOrDefault(playerId, 0);
        int accumulated = accumulatedTicks.getOrDefault(playerId, 0);
        int remainingDaily = Math.max(0, dailyMinutesAllowedInTicks - usedToday);

        if (remainingDaily >= ticksToConsume) {
            ticksUsedToday.put(playerId, usedToday + ticksToConsume);
            return;
        }

        if (remainingDaily > 0) {
            ticksUsedToday.put(playerId, usedToday + remainingDaily);
            ticksToConsume -= remainingDaily;
        }

        if (accumulated > 0) {
            int stackedToConsume = Math.min(ticksToConsume, accumulated);
            accumulatedTicks.put(playerId, accumulated - stackedToConsume);
        }
    }

    private static boolean isExcludedOperator(ServerPlayer player) {
        return Configuration.INSTANCE.excludeOperators
                && player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR);
    }

    private static void checkAndWarnPlayer(ServerPlayer player) {
        UUID playerId = player.getUUID();

        if (isExcludedOperator(player)) {
            warningTicksRemaining.remove(playerId);
            activeWarningStage.remove(playerId);
            return;
        }

        int remaining = ticksRemaining(playerId);

        if (remaining <= 0) {
            warningTicksRemaining.remove(playerId);
            activeWarningStage.remove(playerId);
            return;
        }

        boolean secondWarningReady = Configuration.INSTANCE.secondWarningEnabled
                && secondWarningThresholdInTicks > 0
                && remaining <= secondWarningThresholdInTicks
                && !playersWarnedSecond.contains(playerId);

        if (secondWarningReady) {
            // If the player connects/reloads while already below the second threshold,
            // skip a stale first warning and show the more urgent warning instead.
            playersWarnedFirst.add(playerId);
            playersWarnedSecond.add(playerId);
            startWarning(player, WarningStage.SECOND, remaining);
        } else if (!playersWarnedFirst.contains(playerId)
                && firstWarningThresholdInTicks > 0
                && remaining <= firstWarningThresholdInTicks) {
            playersWarnedFirst.add(playerId);
            startWarning(player, WarningStage.FIRST, remaining);
        }

        Integer visibleTicks = warningTicksRemaining.get(playerId);
        WarningStage stage = activeWarningStage.get(playerId);
        if (visibleTicks == null || visibleTicks <= 0 || stage == null) {
            warningTicksRemaining.remove(playerId);
            activeWarningStage.remove(playerId);
            return;
        }

        String timeMessage = Formatting.ticksAsWords(remaining);
        String template = stage == WarningStage.SECOND
                ? Configuration.INSTANCE.secondWarningMessage
                : Configuration.INSTANCE.warningMessage;
        String warningMessage = template.replace("%time%", timeMessage);
        player.sendOverlayMessage(Component.literal(warningMessage));

        int next = visibleTicks - ticksPerCheck;
        if (next <= 0) {
            warningTicksRemaining.remove(playerId);
            activeWarningStage.remove(playerId);
        } else {
            warningTicksRemaining.put(playerId, next);
        }
    }

    private static void startWarning(ServerPlayer player, WarningStage stage, int remaining) {
        UUID playerId = player.getUUID();
        warningTicksRemaining.put(playerId, WARNING_VISIBLE_TICKS);
        activeWarningStage.put(playerId, stage);

        // Send one persistent copy to normal chat in addition to the repeated
        // action-bar/overlay warning. This makes the warning visible even if
        // another server mod overwrites the action bar.
        String timeMessage = Formatting.ticksAsWords(remaining);
        String template = stage == WarningStage.SECOND
                ? Configuration.INSTANCE.secondWarningMessage
                : Configuration.INSTANCE.warningMessage;
        String warningMessage = template.replace("%time%", timeMessage);
        player.sendSystemMessage(Component.literal(warningMessage));
        LOGGER.info("Time-limit "
                + (stage == WarningStage.FIRST ? "first" : "second")
                + " warning triggered for " + playerId
                + " with " + (remaining / 20) + " seconds remaining");
    }

    public static boolean canPlayerJoin(ServerPlayer player) {
        return ticksRemaining(player.getUUID()) > 0 || isExcludedOperator(player);
    }

    private static void disconnect(ServerGamePacketListenerImpl handler) {
        UUID playerId = handler.getPlayer().getUUID();

        // A disconnect is not completed synchronously. The player may therefore
        // still be present in PlayerList on the next server tick. Guard against
        // requesting the same time-limit disconnect more than once per connection.
        if (!playersDisconnecting.add(playerId)) {
            return;
        }

        handler.disconnect(Component.literal(Configuration.INSTANCE.disconnectMessage));
    }

    @Override
    public void onInitialize() {
        Configuration.load();

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;

            if (tickCounter < ticksPerCheck) {
                return;
            }
            tickCounter = 0;

            LocalDateTime currentTime = LocalDateTime.now();
            boolean midnightPassed = lastTickTime.toLocalDate().isBefore(currentTime.toLocalDate());
            if (midnightPassed) {
                handleDayTransition();
            }

            ArrayList<ServerGamePacketListenerImpl> choppingBlock = new ArrayList<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                UUID uuid = player.getUUID();

                if (!canPlayerJoin(player)) {
                    warningTicksRemaining.remove(uuid);
                    activeWarningStage.remove(uuid);
                    choppingBlock.add(player.connection);
                } else {
                    consumeTime(uuid, ticksPerCheck);
                    checkAndWarnPlayer(player);
                }
            }

            choppingBlock.forEach(ParentalControls::disconnect);
            lastTickTime = currentTime;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // A new connection for this UUID is a fresh connection. Remove any
            // stale guard left from a previous completed disconnect.
            playersDisconnecting.remove(handler.getPlayer().getUUID());

            if (canPlayerJoin(handler.getPlayer())) {
                return;
            }
            disconnect(handler);
        });

        CommandRegistrationCallback.EVENT.register(ParentalControlsCommand::register);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Configuration.INSTANCE.playerAccumulatedTicks.clear();
            Configuration.INSTANCE.playerAccumulatedTicks.putAll(accumulatedTicks);
            Configuration.save();
        }));
    }

    private void handleDayTransition() {
        if (Configuration.INSTANCE.allowTimeStacking) {
            for (UUID playerId : ticksUsedToday.keySet()) {
                int usedToday = ticksUsedToday.get(playerId);
                int leftover = Math.max(0, dailyMinutesAllowedInTicks - usedToday);

                if (leftover > 0) {
                    int currentAccumulated = accumulatedTicks.getOrDefault(playerId, 0);
                    int newAccumulated = Math.min(maxAccumulableHoursInTicks, currentAccumulated + leftover);
                    accumulatedTicks.put(playerId, newAccumulated);
                }
            }

            for (UUID playerId : new HashSet<>(accumulatedTicks.keySet())) {
                if (ticksUsedToday.containsKey(playerId)) {
                    continue;
                }

                int leftover = dailyMinutesAllowedInTicks;
                int currentAccumulated = accumulatedTicks.get(playerId);
                int newAccumulated = Math.min(maxAccumulableHoursInTicks, currentAccumulated + leftover);
                accumulatedTicks.put(playerId, newAccumulated);
            }

            Configuration.INSTANCE.playerAccumulatedTicks.clear();
            Configuration.INSTANCE.playerAccumulatedTicks.putAll(accumulatedTicks);
            Configuration.save();
        }

        ticksUsedToday.clear();
        playersWarnedFirst.clear();
        playersWarnedSecond.clear();
        warningTicksRemaining.clear();
        activeWarningStage.clear();
        playersDisconnecting.clear();
        LOGGER.info("New day started - daily usage reset");
    }
}
