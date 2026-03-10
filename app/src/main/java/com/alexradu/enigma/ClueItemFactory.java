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

    private final EnigmaMod mod;

    public ClueItemFactory(EnigmaMod mod) {
        this.mod = mod;
    }

    public ItemStack createClueItem(EnigmaConfig.HintEntry hint, int hintIndex) {
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

        return stack;
    }

    public boolean isClueItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(KEY_IS_CLUE);
    }
}
