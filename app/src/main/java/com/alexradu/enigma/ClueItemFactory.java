package com.alexradu.enigma;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ClueItemFactory {

    public static final String KEY_IS_CLUE      = "is_clue";
    public static final String KEY_TARGET_X     = "target_x";
    public static final String KEY_TARGET_Y     = "target_y";
    public static final String KEY_TARGET_Z     = "target_z";
    public static final String KEY_HINT_MESSAGE = "hint_message";
    public static final String KEY_HINT_INDEX   = "hint_index";
    public static final String KEY_ADMIN_GIVEN  = "admin_given";

    private final EnigmaMod mod;

    public ClueItemFactory(EnigmaMod mod) {
        this.mod = mod;
    }

    public ItemStack createGameplayClueItem(EnigmaConfig.HintEntry hint, int hintIndex) {
        return createClueItem(hint, hintIndex, false);
    }

    public ItemStack createAdminClueItem(EnigmaConfig.HintEntry hint, int hintIndex) {
        return createClueItem(hint, hintIndex, true);
    }

    public ItemStack createClueItem(EnigmaConfig.HintEntry hint, int hintIndex, boolean adminGiven) {
        var cfg = mod.getEnigmaConfig();

        Item item = Registries.ITEM.getOrEmpty(Identifier.tryParse(cfg.getClueItemMaterial()))
                .orElse(Items.PAPER);
        ItemStack stack = new ItemStack(item);

        // Display name
        stack.setCustomName(Text.literal(cfg.getClueItemName()));

        // Lore — stored as JSON Text strings in display.Lore NBT
        NbtList loreList = new NbtList();
        for (String line : cfg.getClueItemLore()) {
            String processed = line
                    .replace("%x%", String.valueOf(hint.x()))
                    .replace("%y%", String.valueOf(hint.y()))
                    .replace("%z%", String.valueOf(hint.z()));
            // Minimal JSON Text format accepted by Minecraft
            String escaped = processed.replace("\\", "\\\\").replace("\"", "\\\"");
            loreList.add(NbtString.of("{\"text\":\"" + escaped + "\"}"));
        }
        stack.getOrCreateSubNbt("display").put("Lore", loreList);

        // Custom data: coordinates, hint message, index
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(KEY_IS_CLUE, true);
        nbt.putInt(KEY_TARGET_X, hint.x());
        nbt.putInt(KEY_TARGET_Y, hint.y());
        nbt.putInt(KEY_TARGET_Z, hint.z());
        nbt.putString(KEY_HINT_MESSAGE, hint.message());
        nbt.putInt(KEY_HINT_INDEX, hintIndex);
        nbt.putBoolean(KEY_ADMIN_GIVEN, adminGiven);

        return stack;
    }

    public boolean isClueItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(KEY_IS_CLUE);
    }

    public boolean isGameplayClue(ItemStack stack) {
        return isClueItem(stack) && !isAdminGiven(stack);
    }

    public boolean isAdminGiven(ItemStack stack) {
        NbtCompound nbt = stack != null ? stack.getNbt() : null;
        return nbt != null && nbt.getBoolean(KEY_ADMIN_GIVEN);
    }

    public int getHintIndex(ItemStack stack) {
        NbtCompound nbt = stack != null ? stack.getNbt() : null;
        if (nbt == null || !nbt.contains(KEY_HINT_INDEX)) {
            return -1;
        }
        return nbt.getInt(KEY_HINT_INDEX);
    }
}
