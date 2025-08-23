package lol.sylvie.parental.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lol.sylvie.parental.ParentalControls;
import lol.sylvie.parental.config.Configuration;
import lol.sylvie.parental.util.Formatting;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.apache.commons.lang3.time.DurationFormatUtils;
import java.util.UUID;

public class ParentalControlsCommand {
    public static void register(CommandDispatcher<ServerCommandSource> dispatcher,
                                CommandRegistryAccess registryAccess,
                                CommandManager.RegistrationEnvironment environment) {
        LiteralArgumentBuilder<ServerCommandSource> root = CommandManager.literal("parental");

        root.then(CommandManager.literal("remaining").executes(ctx -> {
            ServerCommandSource source = ctx.getSource();

            ServerPlayerEntity player = source.getPlayerOrThrow();
            if (player.hasPermissionLevel(4) && Configuration.INSTANCE.excludeOperators) {
                source.sendError(Text.literal("You are immune to the time limit."));
                return 0;
            }

            UUID playerId = player.getUuid();
            int ticksRemaining = ParentalControls.ticksRemaining(playerId);
            String formatted = Formatting.ticksAsHours(ticksRemaining);
            StringBuilder message = new StringBuilder("You have §l" + formatted + "§r remaining.");

            if (Configuration.INSTANCE.allowTimeStacking) {
                int accumulated = ParentalControls.accumulatedTicks.getOrDefault(playerId, 0);

                String accumulatedFormatted = Formatting.ticksAsHours(accumulated);
                message.append("\n§7Stacked: §f").append(accumulatedFormatted);
            }

            source.sendMessage(Text.literal(message.toString()));
            return 1;
        }));

        root.then(CommandManager.literal("reload").requires(s -> s.hasPermissionLevel(4)).executes(ctx -> {
            ServerCommandSource source = ctx.getSource();
            if (Configuration.load()) {
                source.sendFeedback(() -> Text.literal("§aSuccessfully reloaded!"), true);
            } else {
                source.sendError(Text.literal("There was an error while trying to load the configuration! Check console for details."));
            }
            return 1;
        }));

        root.then(CommandManager.literal("stacking").requires(s -> s.hasPermissionLevel(4)).executes(ctx -> {
            ServerCommandSource source = ctx.getSource();
            
            if (!Configuration.INSTANCE.allowTimeStacking) {
                source.sendMessage(Text.literal("§eTime stacking is disabled."));
                return 1;
            }
            
            source.sendMessage(Text.literal("§6=== Time Stacking ==="));
            source.sendMessage(Text.literal("§7Time stacking: §aEnabled"));
            source.sendMessage(Text.literal("§7Max stacked: §f" + Configuration.INSTANCE.maxStackedHours + " hours"));

            if (ParentalControls.accumulatedTicks.isEmpty()) {
                source.sendMessage(Text.literal("§7No players have accumulated time."));
            } else {
                source.sendMessage(Text.literal("§7Players with stacked time:"));
                ParentalControls.accumulatedTicks.forEach((playerId, ticks) -> {
                    String formatted = Formatting.ticksAsHours(ticks);
                    source.sendMessage(Text.literal("§f" + playerId + "§7: §a" + formatted));
                });
            }
            
            return 1;
        }));

        dispatcher.register(root);
    }
}
