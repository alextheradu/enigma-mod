package com.alexradu.enigma.mixin;

import com.alexradu.enigma.EnigmaMod;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(ItemEntity.class)
public abstract class ClueItemPickupMixin {

    @Shadow public abstract ItemStack getStack();

    @Unique private int enigmaOriginalCount = -1;
    @Unique private int enigmaHintIndex = -1;
    @Unique private @Nullable UUID enigmaPickupPlayerUuid = null;

    @Inject(method = "onPlayerCollision", at = @At("HEAD"), cancellable = true)
    private void enigma$blockDuplicatePickup(PlayerEntity player, CallbackInfo ci) {
        enigma$resetPickupState();

        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;

        var mod = EnigmaMod.getInstance();
        if (mod == null) return;

        ItemStack stack = this.getStack();
        if (!mod.getClueItemFactory().isGameplayClue(stack)) return;

        int hintIndex = mod.getClueItemFactory().getHintIndex(stack);
        if (hintIndex < 0) return;

        if (mod.getPlayerDataManager().hasReceived(serverPlayer.getUuid(), hintIndex)) {
            serverPlayer.sendMessage(Text.literal("You already know this clue."), false);
            ci.cancel();
            return;
        }

        enigmaOriginalCount = stack.getCount();
        enigmaHintIndex = hintIndex;
        enigmaPickupPlayerUuid = serverPlayer.getUuid();
    }

    @Inject(method = "onPlayerCollision", at = @At("RETURN"))
    private void enigma$markSuccessfulPickup(PlayerEntity player, CallbackInfo ci) {
        if (enigmaHintIndex < 0 || enigmaPickupPlayerUuid == null) return;

        var mod = EnigmaMod.getInstance();
        if (mod == null) {
            enigma$resetPickupState();
            return;
        }

        ItemStack remainingStack = this.getStack();
        if (remainingStack.isEmpty() || remainingStack.getCount() < enigmaOriginalCount) {
            mod.getPlayerDataManager().markReceived(enigmaPickupPlayerUuid, enigmaHintIndex);
        }

        enigma$resetPickupState();
    }

    @Unique
    private void enigma$resetPickupState() {
        enigmaOriginalCount = -1;
        enigmaHintIndex = -1;
        enigmaPickupPlayerUuid = null;
    }
}
