package com.alexradu.enigma.commands;

import com.alexradu.enigma.EnigmaMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class EnigmaCommand {

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher, EnigmaMod mod) {
        dispatcher.register(
                CommandManager.literal("enigma")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> sendHelp(ctx.getSource()))
                        .then(CommandManager.literal("start")
                                .executes(ctx -> startHunt(ctx.getSource(), mod)))
                        .then(CommandManager.literal("stop")
                                .executes(ctx -> stopHunt(ctx.getSource(), mod)))
                        .then(CommandManager.literal("reload")
                                .executes(ctx -> reloadConfig(ctx.getSource(), mod)))
                        .then(CommandManager.literal("give")
                                .then(CommandManager.argument("player", StringArgumentType.word())
                                        .suggests((ctx, builder) -> {
                                            ctx.getSource().getServer().getPlayerManager()
                                                    .getPlayerList().stream()
                                                    .map(p -> p.getGameProfile().getName())
                                                    .filter(n -> n.toLowerCase().startsWith(builder.getRemaining().toLowerCase()))
                                                    .forEach(builder::suggest);
                                            return builder.buildFuture();
                                        })
                                        .executes(ctx -> giveHints(ctx.getSource(), mod,
                                                StringArgumentType.getString(ctx, "player")))))
        );
    }

    private static int startHunt(ServerCommandSource src, EnigmaMod mod) {
        mod.getEnigmaConfig().setHuntActive(true);
        src.getServer().getPlayerManager().broadcast(
                Text.literal("The Enigma Hunt has begun! Find the clues...").formatted(Formatting.GOLD, Formatting.BOLD),
                false);
        src.sendFeedback(() -> Text.literal("Hunt started.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int stopHunt(ServerCommandSource src, EnigmaMod mod) {
        mod.getEnigmaConfig().setHuntActive(false);
        src.getServer().getPlayerManager().broadcast(
                Text.literal("The Enigma Hunt has ended.").formatted(Formatting.RED, Formatting.BOLD),
                false);
        src.sendFeedback(() -> Text.literal("Hunt stopped.").formatted(Formatting.RED), false);
        return 1;
    }

    private static int reloadConfig(ServerCommandSource src, EnigmaMod mod) {
        mod.getEnigmaConfig().load();
        src.sendFeedback(() -> Text.literal("Config reloaded.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int giveHints(ServerCommandSource src, EnigmaMod mod, String playerName) {
        ServerPlayerEntity target = src.getServer().getPlayerManager().getPlayer(playerName);
        if (target == null) {
            src.sendError(Text.literal("Player not found: " + playerName));
            return 0;
        }
        var hints = mod.getEnigmaConfig().getHints();
        if (hints.isEmpty()) {
            src.sendError(Text.literal("No hints configured."));
            return 0;
        }
        List<Integer> unreceived = mod.getPlayerDataManager()
                .getUnreceivedIndices(target.getUuid(), hints.size());
        if (unreceived.isEmpty()) {
            src.sendFeedback(() -> Text.literal(target.getGameProfile().getName()
                    + " has already received all hints.").formatted(Formatting.YELLOW), false);
            return 1;
        }
        for (int index : unreceived) {
            target.giveItemStack(mod.getClueItemFactory().createClueItem(hints.get(index), index));
            mod.getPlayerDataManager().markReceived(target.getUuid(), index);
        }
        int count = unreceived.size();
        src.sendFeedback(() -> Text.literal("Gave " + count + " clue(s) to "
                + target.getGameProfile().getName() + ".").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int sendHelp(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
                "Enigma Commands:\n" +
                " /enigma start - Start the hunt\n" +
                " /enigma stop - Stop the hunt\n" +
                " /enigma reload - Reload config\n" +
                " /enigma give <player> - Give all unreceived clues"
        ).formatted(Formatting.GOLD), false);
        return 1;
    }
}
