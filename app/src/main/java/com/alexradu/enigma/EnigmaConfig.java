package com.alexradu.enigma;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class EnigmaConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;
    private JsonObject root;

    private boolean huntActive;
    private String secretString;
    private String rewardCommand;
    private SignAction signAction;
    private String clueItemMaterial;
    private String clueItemName;
    private List<String> clueItemLore;
    private List<ChestEntry> lootChests;
    private List<MobEntry> mobDrops;
    private List<HintEntry> hints;

    public EnigmaConfig() {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("enigma");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            EnigmaMod.LOGGER.error("Failed to create config directory", e);
        }
        this.configPath = dir.resolve("config.json");
        load();
    }

    public void load() {
        if (!Files.exists(configPath)) {
            saveDefault();
        }
        try (Reader reader = Files.newBufferedReader(configPath)) {
            root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) root = new JsonObject();
        } catch (IOException e) {
            EnigmaMod.LOGGER.error("Failed to load config", e);
            root = new JsonObject();
        }
        parse();
    }

    private void parse() {
        huntActive  = bool("hunt-active", false);
        secretString  = str("secret-string", "opensesame");
        rewardCommand = str("reward-command", "say %player% has solved the Enigma!");
        try {
            signAction = SignAction.valueOf(str("sign-action", "HIDE_TEXT").toUpperCase());
        } catch (IllegalArgumentException e) {
            signAction = SignAction.HIDE_TEXT;
        }

        JsonObject item = root.has("clue-item") ? root.getAsJsonObject("clue-item") : new JsonObject();
        clueItemMaterial = item.has("material") ? item.get("material").getAsString() : "minecraft:paper";
        clueItemName     = item.has("name")     ? item.get("name").getAsString()     : "Mysterious Clue";
        clueItemLore = new ArrayList<>();
        if (item.has("lore")) {
            for (JsonElement e : item.getAsJsonArray("lore")) clueItemLore.add(e.getAsString());
        }

        lootChests = new ArrayList<>();
        if (root.has("loot-chests")) {
            for (JsonElement e : root.getAsJsonArray("loot-chests")) {
                JsonObject o = e.getAsJsonObject();
                lootChests.add(new ChestEntry(o.get("table").getAsString(), o.get("chance").getAsDouble()));
            }
        }

        mobDrops = new ArrayList<>();
        if (root.has("mob-drops")) {
            for (JsonElement e : root.getAsJsonArray("mob-drops")) {
                JsonObject o = e.getAsJsonObject();
                mobDrops.add(new MobEntry(o.get("type").getAsString(), o.get("chance").getAsDouble()));
            }
        }

        hints = new ArrayList<>();
        if (root.has("hints")) {
            for (JsonElement e : root.getAsJsonArray("hints")) {
                JsonObject o = e.getAsJsonObject();
                hints.add(new HintEntry(
                        o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
                        o.get("message").getAsString()));
            }
        }
    }

    private void saveDefault() {
        try (InputStream in = getClass().getResourceAsStream("/config.json")) {
            if (in != null) Files.copy(in, configPath);
        } catch (IOException e) {
            EnigmaMod.LOGGER.error("Failed to save default config", e);
        }
    }

    public void setHuntActive(boolean active) {
        this.huntActive = active;
        root.addProperty("hunt-active", active);
        try (Writer w = Files.newBufferedWriter(configPath)) {
            GSON.toJson(root, w);
        } catch (IOException e) {
            EnigmaMod.LOGGER.error("Failed to save config", e);
        }
    }

    private boolean bool(String key, boolean def) {
        return root.has(key) ? root.get(key).getAsBoolean() : def;
    }

    private String str(String key, String def) {
        return root.has(key) ? root.get(key).getAsString() : def;
    }

    public boolean isHuntActive()         { return huntActive; }
    public String getSecretString()        { return secretString; }
    public String getRewardCommand()       { return rewardCommand; }
    public SignAction getSignAction()      { return signAction; }
    public String getClueItemMaterial()    { return clueItemMaterial; }
    public String getClueItemName()        { return clueItemName; }
    public List<String> getClueItemLore() { return clueItemLore; }
    public List<ChestEntry> getLootChests() { return lootChests; }
    public List<MobEntry> getMobDrops()    { return mobDrops; }
    public List<HintEntry> getHints()      { return hints; }

    public enum SignAction { HIDE_TEXT, BREAK }

    public record ChestEntry(String table, double chance) {}
    // type is the full registry ID, e.g. "minecraft:evoker"
    public record MobEntry(String type, double chance) {}
    public record HintEntry(int x, int y, int z, String message) {}
}
