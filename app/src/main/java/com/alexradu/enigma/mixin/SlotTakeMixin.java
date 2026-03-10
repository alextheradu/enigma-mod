package com.alexradu.enigma.mixin;

import com.alexradu.enigma.EnigmaMod;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Slot.class)
public abstract class SlotTakeMixin {

    @Shadow @Final public Inventory inventory;
    @Shadow public abstract ItemStack getStack();

    @Inject(method = "takeStackRange", at = @At("HEAD"), cancellable = true)
    private void enigma$blockDuplicateContainerTake(int min, int max, PlayerEntity player, CallbackInfoReturnable<ItemStack> cir) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (this.inventory == serverPlayer.getInventory()) return;

        var mod = EnigmaMod.getInstance();
        if (mod == null) return;

        ItemStack stack = this.getStack();
        if (!mod.getClueItemFactory().isGameplayClue(stack)) return;

        int hintIndex = mod.getClueItemFactory().getHintIndex(stack);
        if (hintIndex < 0) return;

        if (mod.getPlayerDataManager().hasReceived(serverPlayer.getUuid(), hintIndex)) {
            serverPlayer.sendMessage(Text.literal("You already know this clue."), false);
            cir.setReturnValue(ItemStack.EMPTY);
        }
    }

    @Inject(method = "takeStackRange", at = @At("RETURN"))
    private void enigma$markSuccessfulContainerTake(int min, int max, PlayerEntity player, CallbackInfoReturnable<ItemStack> cir) {
        if (!(player instanceof ServerPlayerEntity serverPlayer)) return;
        if (this.inventory == serverPlayer.getInventory()) return;

        var mod = EnigmaMod.getInstance();
        if (mod == null) return;

        ItemStack takenStack = cir.getReturnValue();
        if (!mod.getClueItemFactory().isGameplayClue(takenStack)) return;

        int hintIndex = mod.getClueItemFactory().getHintIndex(takenStack);
        if (hintIndex >= 0) {
            mod.getPlayerDataManager().markReceived(serverPlayer.getUuid(), hintIndex);
        }
    }
}
