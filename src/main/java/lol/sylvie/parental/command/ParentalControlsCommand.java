package lol.sylvie.parental.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import lol.sylvie.parental.ParentalControls;
import lol.sylvie.parental.config.Configuration;
import lol.sylvie.parental.util.Formatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import org.apache.commons.lang3.time.DurationFormatUtils;
import java.util.UUID;

public class ParentalControlsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher,
                                CommandBuildContext registryAccess,
                                Commands.CommandSelection environment) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("parental");

        root.then(Commands.literal("remaining").executes(ctx -> {
            CommandSourceStack source = ctx.getSource();

            ServerPlayer player = source.getPlayerOrException();
            if (player.permissions().hasPermission(Permissions.COMMANDS_MODERATOR) && Configuration.INSTANCE.excludeOperators) {
                source.sendFailure(Component.literal("You are immune to the time limit."));
                return 0;
            }

            UUID playerId = player.getUUID();
            int ticksRemaining = ParentalControls.ticksRemaining(playerId);
            String formatted = Formatting.ticksAsHours(ticksRemaining);
            StringBuilder message = new StringBuilder("You have §l" + formatted + "§r remaining.");

            if (Configuration.INSTANCE.allowTimeStacking) {
                int accumulated = ParentalControls.accumulatedTicks.getOrDefault(playerId, 0);

                String accumulatedFormatted = Formatting.ticksAsHours(accumulated);
                message.append("\n§7Stacked: §f").append(accumulatedFormatted);
            }

            source.sendSystemMessage(Component.literal(message.toString()));
            return 1;
        }));

        root.then(Commands.literal("reload").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)).executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            if (Configuration.load()) {
                source.sendSuccess(() -> Component.literal("§aSuccessfully reloaded!"), true);
            } else {
                source.sendFailure(Component.literal("There was an error while trying to load the configuration! Check console for details."));
            }
            return 1;
        }));

        root.then(Commands.literal("stacking").requires(s -> s.permissions().hasPermission(Permissions.COMMANDS_MODERATOR)).executes(ctx -> {
            CommandSourceStack source = ctx.getSource();
            
            if (!Configuration.INSTANCE.allowTimeStacking) {
                source.sendSystemMessage(Component.literal("§eTime stacking is disabled."));
                return 1;
            }
            
            source.sendSystemMessage(Component.literal("§6=== Time Stacking ==="));
            source.sendSystemMessage(Component.literal("§7Time stacking: §aEnabled"));
            source.sendSystemMessage(Component.literal("§7Max stacked: §f" + Configuration.INSTANCE.maxStackedHours + " hours"));

            if (ParentalControls.accumulatedTicks.isEmpty()) {
                source.sendSystemMessage(Component.literal("§7No players have accumulated time."));
            } else {
                source.sendSystemMessage(Component.literal("§7Players with stacked time:"));
                ParentalControls.accumulatedTicks.forEach((playerId, ticks) -> {
                    String formatted = Formatting.ticksAsHours(ticks);
                    source.sendSystemMessage(Component.literal("§f" + playerId + "§7: §a" + formatted));
                });
            }
            
            return 1;
        }));

        dispatcher.register(root);
    }
}
