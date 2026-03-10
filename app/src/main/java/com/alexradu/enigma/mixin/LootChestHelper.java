package com.alexradu.enigma.mixin;

import net.minecraft.item.ItemStack;

import java.util.Random;

/** Static helpers shared by LootChestMixin (interface mixins cannot have instance fields). */
final class LootChestHelper {
    static final ThreadLocal<ItemStack> PENDING_CLUE = new ThreadLocal<>();
    static final Random RANDOM = new Random();

    private LootChestHelper() {}
}
