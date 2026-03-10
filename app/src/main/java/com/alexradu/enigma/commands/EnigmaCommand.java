package com.alexradu.enigma.commands;

import com.alexradu.enigma.ClueItemFactory;
import com.alexradu.enigma.EnigmaMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public class EnigmaCommand {

    private static final int DEFAULT_CHEST_RADIUS = 64;
    private static final int MAX_CHEST_RADIUS = 128;

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
                        .then(CommandManager.literal("chests")
                                .executes(ctx -> listNearbyChests(ctx.getSource(), mod, DEFAULT_CHEST_RADIUS))
                                .then(CommandManager.argument("radius", IntegerArgumentType.integer(1))
                                        .executes(ctx -> listNearbyChests(
                                                ctx.getSource(),
                                                mod,
                                                IntegerArgumentType.getInteger(ctx, "radius")))))
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

        for (int index = 0; index < hints.size(); index++) {
            target.giveItemStack(mod.getClueItemFactory().createAdminClueItem(hints.get(index), index));
        }

        int count = hints.size();
        src.sendFeedback(() -> Text.literal("Gave " + count + " clue(s) to "
                + target.getGameProfile().getName() + ".").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int listNearbyChests(ServerCommandSource src, EnigmaMod mod, int radius) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            src.sendError(Text.literal("This command can only be used by a player."));
            return 0;
        }

        int clampedRadius = Math.min(Math.max(radius, 1), MAX_CHEST_RADIUS);
        BlockPos origin = player.getBlockPos();
        var world = player.getServerWorld();
        int chunkRadius = (clampedRadius + 15) / 16;
        List<ChestScanResult> results = new ArrayList<>();

        for (int chunkX = (origin.getX() >> 4) - chunkRadius; chunkX <= (origin.getX() >> 4) + chunkRadius; chunkX++) {
            for (int chunkZ = (origin.getZ() >> 4) - chunkRadius; chunkZ <= (origin.getZ() >> 4) + chunkRadius; chunkZ++) {
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                if (chunk == null) continue;

                for (BlockPos pos : chunk.getBlockEntityPositions()) {
                    if (distanceSquared(origin, pos) > (long) clampedRadius * clampedRadius) continue;

                    var blockEntity = world.getBlockEntity(pos);
                    if (!(blockEntity instanceof ChestBlockEntity chest)) continue;

                    ChestScanResult result = scanChest(chest, pos, mod.getClueItemFactory(), origin);
                    if (result != null) {
                        results.add(result);
                    }
                }
            }
        }

        results.sort(Comparator.comparingLong(ChestScanResult::distanceSquared)
                .thenComparing(result -> result.pos().getY())
                .thenComparing(result -> result.pos().getX())
                .thenComparing(result -> result.pos().getZ()));

        if (results.isEmpty()) {
            src.sendFeedback(() -> Text.literal("No nearby chests with Enigma clue papers found.")
                    .formatted(Formatting.YELLOW), false);
            return 1;
        }

        int finalRadius = clampedRadius;
        src.sendFeedback(() -> Text.literal("Nearby Enigma clue chests (" + results.size()
                + ") within " + finalRadius + " blocks:")
                .formatted(Formatting.GOLD), false);

        for (ChestScanResult result : results) {
            src.sendFeedback(() -> Text.literal(String.format(
                    "%d, %d, %d - %d clue paper(s), hints %s",
                    result.pos().getX(),
                    result.pos().getY(),
                    result.pos().getZ(),
                    result.paperCount(),
                    result.hintIndices()))
                    .formatted(Formatting.YELLOW), false);
        }
        return results.size();
    }

    private static ChestScanResult scanChest(Inventory chest, BlockPos pos, ClueItemFactory clueItemFactory, BlockPos origin) {
        int paperCount = 0;
        Set<Integer> hintIndices = new TreeSet<>();

        for (int slot = 0; slot < chest.size(); slot++) {
            ItemStack stack = chest.getStack(slot);
            if (!clueItemFactory.isClueItem(stack)) continue;

            paperCount += stack.getCount();
            int hintIndex = clueItemFactory.getHintIndex(stack);
            if (hintIndex >= 0) {
                hintIndices.add(hintIndex);
            }
        }

        if (paperCount == 0) {
            return null;
        }

        return new ChestScanResult(pos.toImmutable(), paperCount, hintIndices, distanceSquared(origin, pos));
    }

    private static long distanceSquared(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static int sendHelp(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
                "Enigma Commands:\n" +
                " /enigma start - Start the hunt\n" +
                " /enigma stop - Stop the hunt\n" +
                " /enigma reload - Reload config\n" +
                " /enigma give <player> - Give the full clue set\n" +
                " /enigma chests [radius] - List nearby clue chests"
        ).formatted(Formatting.GOLD), false);
        return 1;
    }

    private record ChestScanResult(BlockPos pos, int paperCount, Set<Integer> hintIndices, long distanceSquared) {
    }
}
