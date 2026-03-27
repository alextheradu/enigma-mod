package com.alexradu.enigma.handlers;

import com.alexradu.enigma.ClueItemFactory;
import com.alexradu.enigma.EnigmaMod;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

public class InteractHandler {

    public static void register(EnigmaMod mod) {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (hand != Hand.MAIN_HAND) return ActionResult.PASS;

            ItemStack stack = player.getMainHandStack();
            if (!mod.getClueItemFactory().isClueItem(stack)) return ActionResult.PASS;

            NbtCompound nbt = stack.getNbt();
            if (nbt == null) return ActionResult.PASS;
            int hintIndex = mod.getClueItemFactory().getHintIndex(stack);
            if (hintIndex < 0) return ActionResult.PASS;

            int tx = nbt.getInt(ClueItemFactory.KEY_TARGET_X);
            int ty = nbt.getInt(ClueItemFactory.KEY_TARGET_Y);
            int tz = nbt.getInt(ClueItemFactory.KEY_TARGET_Z);

            var pos = hitResult.getBlockPos();
            if (pos.getX() != tx || pos.getY() != ty || pos.getZ() != tz) {
                return ActionResult.PASS;
            }

            if (world.isClient()) {
                return ActionResult.PASS;
            }
            if (!(player instanceof ServerPlayerEntity serverPlayer)) {
                return ActionResult.PASS;
            }

            int totalHints = mod.getEnigmaConfig().getHints().size();
            int nextRequiredIndex = mod.getPlayerDataManager().getNextRedeemIndex(serverPlayer.getUuid(), totalHints);
            if (nextRequiredIndex >= totalHints) {
                serverPlayer.sendMessage(Text.literal(
                        "You already redeemed every clue. Final step: write \"" +
                                mod.getEnigmaConfig().getSecretString() + "\" on a sign."), false);
                return ActionResult.CONSUME;
            }
            if (hintIndex != nextRequiredIndex) {
                if (mod.getPlayerDataManager().hasRedeemed(serverPlayer.getUuid(), hintIndex)) {
                    serverPlayer.sendMessage(Text.literal("You already redeemed this clue."), false);
                } else {
                    serverPlayer.sendMessage(Text.literal(
                            "Redeem clue #" + (nextRequiredIndex + 1) + " first."), false);
                }
                return ActionResult.CONSUME;
            }

            String hintMessage = nbt.getString(ClueItemFactory.KEY_HINT_MESSAGE);
            serverPlayer.sendMessage(Text.literal(hintMessage), false);
            mod.getPlayerDataManager().markRedeemed(serverPlayer.getUuid(), hintIndex);

            if (stack.getCount() > 1) {
                stack.decrement(1);
            } else {
                serverPlayer.setStackInHand(hand, ItemStack.EMPTY);
            }

            serverPlayer.getInventory().markDirty();
            serverPlayer.currentScreenHandler.sendContentUpdates();

            if (mod.getPlayerDataManager().hasRedeemedAll(serverPlayer.getUuid(), totalHints)) {
                serverPlayer.sendMessage(Text.literal(
                        "Final clue unlocked: write \"" +
                                mod.getEnigmaConfig().getSecretString() + "\" on a sign to solve the Enigma."),
                        false);
            } else {
                int nextIndex = mod.getPlayerDataManager().getNextRedeemIndex(serverPlayer.getUuid(), totalHints);
                serverPlayer.sendMessage(Text.literal(
                        "Clue redeemed. Next required clue: #" + (nextIndex + 1) + "."),
                        false);
            }
            return ActionResult.CONSUME;
        });
    }
}
