package com.alexradu.enigma.commands;

import com.alexradu.enigma.ClueItemFactory;
import com.alexradu.enigma.EnigmaMod;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.inventory.Inventory;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.context.LootContextParameterSet;
import net.minecraft.loot.context.LootContextParameters;
import net.minecraft.loot.context.LootContextTypes;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

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
                        .then(CommandManager.literal("resetAll")
                                .executes(ctx -> resetAll(ctx.getSource(), mod)))
                        .then(CommandManager.literal("setChest")
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(ctx -> setChest(
                                                ctx.getSource(),
                                                mod,
                                                BlockPosArgumentType.getLoadedBlockPos(ctx, "pos")))))
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

    private static int resetAll(ServerCommandSource src, EnigmaMod mod) {
        mod.getPlayerDataManager().resetAll();

        int removedClues = 0;
        for (ServerPlayerEntity player : src.getServer().getPlayerManager().getPlayerList()) {
            removedClues += removeClues(player.getInventory(), mod.getClueItemFactory());
            removedClues += removeClues(player.getEnderChestInventory(), mod.getClueItemFactory());
            player.playerScreenHandler.sendContentUpdates();
        }

        int finalRemovedClues = removedClues;
        src.sendFeedback(() -> Text.literal("Reset all player Enigma progress and removed "
                + finalRemovedClues + " clue item(s) from online players.")
                .formatted(Formatting.GREEN), true);
        return 1;
    }

    private static int setChest(ServerCommandSource src, EnigmaMod mod, BlockPos pos) {
        var lootEntries = mod.getEnigmaConfig().getLootChests();
        var hints = mod.getEnigmaConfig().getHints();
        if (lootEntries.isEmpty()) {
            src.sendError(Text.literal("No loot-chests are configured."));
            return 0;
        }
        if (hints.isEmpty()) {
            src.sendError(Text.literal("No hints are configured."));
            return 0;
        }

        var world = src.getWorld();
        Direction chestFacing = getChestFacing(src, pos);
        world.setBlockState(pos, Blocks.CHEST.getDefaultState().with(ChestBlock.FACING, chestFacing), 3);

        if (!(world.getBlockEntity(pos) instanceof ChestBlockEntity chest)) {
            src.sendError(Text.literal("Failed to create a chest at the target position."));
            return 0;
        }

        chest.clear();

        var chosenLoot = lootEntries.get(ThreadLocalRandom.current().nextInt(lootEntries.size()));
        Identifier lootTableId = Identifier.tryParse(chosenLoot.table());
        if (lootTableId == null) {
            src.sendError(Text.literal("Configured loot table is invalid: " + chosenLoot.table()));
            return 0;
        }

        var lootTable = src.getServer().getLootManager().getLootTable(lootTableId);
        var lootContext = new LootContextParameterSet.Builder(world)
                .add(LootContextParameters.ORIGIN, Vec3d.ofCenter(pos))
                .build(LootContextTypes.CHEST);
        lootTable.supplyInventory(chest, lootContext, ThreadLocalRandom.current().nextLong());

        int hintIndex = chooseDemoHintIndex(src, mod, hints.size());
        addStackToInventory(chest, mod.getClueItemFactory().createGameplayClueItem(hints.get(hintIndex), hintIndex));

        chest.markDirty();
        world.updateListeners(pos, world.getBlockState(pos), world.getBlockState(pos), 3);

        int finalHintIndex = hintIndex;
        src.sendFeedback(() -> Text.literal("Created demo chest at "
                + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()
                + " using loot table " + lootTableId
                + " with guaranteed clue #" + finalHintIndex + ".").formatted(Formatting.GREEN), false);
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
                + target.getGameProfile().getName()
                + ". They must redeem them in order.").formatted(Formatting.GREEN), false);
        return 1;
    }

    private static int chooseDemoHintIndex(ServerCommandSource src, EnigmaMod mod, int totalHints) {
        if (src.getEntity() instanceof ServerPlayerEntity player) {
            var unseen = mod.getPlayerDataManager().getUnreceivedIndices(player.getUuid(), totalHints);
            if (!unseen.isEmpty()) {
                return unseen.get(ThreadLocalRandom.current().nextInt(unseen.size()));
            }
        }
        return ThreadLocalRandom.current().nextInt(totalHints);
    }

    private static void addStackToInventory(Inventory inventory, ItemStack stack) {
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.getStack(slot).isEmpty()) {
                inventory.setStack(slot, stack);
                return;
            }
        }
        inventory.setStack(inventory.size() - 1, stack);
    }

    private static int removeClues(Inventory inventory, ClueItemFactory clueItemFactory) {
        int removed = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (!clueItemFactory.isClueItem(stack)) {
                continue;
            }
            removed += stack.getCount();
            inventory.setStack(slot, ItemStack.EMPTY);
        }
        if (inventory instanceof PlayerInventory playerInventory) {
            playerInventory.markDirty();
        }
        return removed;
    }

    private static Direction getChestFacing(ServerCommandSource src, BlockPos chestPos) {
        if (!(src.getEntity() instanceof ServerPlayerEntity player)) {
            return Direction.NORTH;
        }

        double dx = player.getX() - (chestPos.getX() + 0.5);
        double dz = player.getZ() - (chestPos.getZ() + 0.5);

        if (Math.abs(dx) > Math.abs(dz)) {
            return dx >= 0.0 ? Direction.EAST : Direction.WEST;
        }
        return dz >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private static int sendHelp(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal(
                "Enigma Commands:\n" +
                " /enigma start - Start the hunt\n" +
                " /enigma stop - Stop the hunt\n" +
                " /enigma reload - Reload config\n" +
                " /enigma give <player> - Give the full clue set (redeem in order)\n" +
                " /enigma resetAll - Reset all player clue data\n" +
                " /enigma setChest <x y z> - Create a demo clue chest"
        ).formatted(Formatting.GOLD), false);
        return 1;
    }
}
