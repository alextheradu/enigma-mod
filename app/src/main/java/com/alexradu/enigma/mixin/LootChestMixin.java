package com.alexradu.enigma.mixin;

import com.alexradu.enigma.EnigmaMod;
import net.minecraft.block.entity.LootableContainerBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

/**
 * Intercepts loot-chest generation (1.20.1: checkLootInteraction lives on the class).
 * Captures the loot-table identifier at HEAD, injects a random clue item at RETURN.
 */
@Mixin(LootableContainerBlockEntity.class)
public abstract class LootChestMixin {

    @Shadow @Nullable protected Identifier lootTableId;

    @Unique private static final Random ENIGMA_RANDOM = new Random();
    @Unique private @Nullable ItemStack enigmaPendingClue = null;

    @Inject(method = "checkLootInteraction", at = @At("HEAD"))
    private void enigma$captureClue(@Nullable PlayerEntity player, CallbackInfo ci) {
        enigmaPendingClue = null;
        if (this.lootTableId == null) return;

        var mod = EnigmaMod.getInstance();
        if (mod == null || !mod.getEnigmaConfig().isHuntActive()) return;

        String tableKey = this.lootTableId.toString();
        for (var entry : mod.getEnigmaConfig().getLootChests()) {
            if (entry.table().equalsIgnoreCase(tableKey)) {
                if (ENIGMA_RANDOM.nextDouble() * 100.0 < entry.chance()) {
                    var hints = mod.getEnigmaConfig().getHints();
                    if (!hints.isEmpty()) {
                        int index = ENIGMA_RANDOM.nextInt(hints.size());
                        enigmaPendingClue = mod.getClueItemFactory()
                                .createClueItem(hints.get(index), index);
                    }
                }
                break;
            }
        }
    }

    @Inject(method = "checkLootInteraction", at = @At("RETURN"))
    private void enigma$addClue(@Nullable PlayerEntity player, CallbackInfo ci) {
        if (enigmaPendingClue == null) return;
        Inventory inv = (Inventory) (Object) this;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.getStack(i).isEmpty()) {
                inv.setStack(i, enigmaPendingClue);
                enigmaPendingClue = null;
                return;
            }
        }
        enigmaPendingClue = null;
    }
}
