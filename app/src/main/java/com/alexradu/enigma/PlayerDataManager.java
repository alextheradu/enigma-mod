package com.alexradu.enigma;

import com.google.gson.*;
import net.fabricmc.loader.api.FabricLoader;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class PlayerDataManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path dataPath;
    private final Map<UUID, Set<Integer>> receivedHints = new HashMap<>();
    private final Map<UUID, Set<Integer>> redeemedHints = new HashMap<>();

    public PlayerDataManager(EnigmaMod mod) {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("enigma");
        this.dataPath = dir.resolve("data.json");
        load();
    }

    public void load() {
        receivedHints.clear();
        redeemedHints.clear();
        if (!Files.exists(dataPath)) return;
        try (Reader reader = Files.newBufferedReader(dataPath)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            for (var entry : root.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    JsonElement value = entry.getValue();

                    if (value.isJsonArray()) {
                        Set<Integer> legacyReceived = new HashSet<>();
                        for (JsonElement e : value.getAsJsonArray()) {
                            legacyReceived.add(e.getAsInt());
                        }
                        receivedHints.put(uuid, legacyReceived);
                        continue;
                    }

                    JsonObject playerObject = value.getAsJsonObject();
                    receivedHints.put(uuid, readIndexSet(playerObject, "received"));
                    redeemedHints.put(uuid, readIndexSet(playerObject, "redeemed"));
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            EnigmaMod.LOGGER.error("Failed to load player data", e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        Set<UUID> uuids = new HashSet<>();
        uuids.addAll(receivedHints.keySet());
        uuids.addAll(redeemedHints.keySet());

        for (UUID uuid : uuids) {
            JsonObject playerObject = new JsonObject();
            playerObject.add("received", toJsonArray(receivedHints.getOrDefault(uuid, Set.of())));
            playerObject.add("redeemed", toJsonArray(redeemedHints.getOrDefault(uuid, Set.of())));
            root.add(uuid.toString(), playerObject);
        }
        try (Writer writer = Files.newBufferedWriter(dataPath)) {
            GSON.toJson(root, writer);
        } catch (IOException e) {
            EnigmaMod.LOGGER.error("Failed to save player data", e);
        }
    }

    public void markReceived(UUID uuid, int hintIndex) {
        receivedHints.computeIfAbsent(uuid, k -> new HashSet<>()).add(hintIndex);
        save();
    }

    public void markRedeemed(UUID uuid, int hintIndex) {
        redeemedHints.computeIfAbsent(uuid, k -> new HashSet<>()).add(hintIndex);
        save();
    }

    public boolean hasReceived(UUID uuid, int hintIndex) {
        return receivedHints.getOrDefault(uuid, Set.of()).contains(hintIndex);
    }

    public boolean hasRedeemed(UUID uuid, int hintIndex) {
        return redeemedHints.getOrDefault(uuid, Set.of()).contains(hintIndex);
    }

    public List<Integer> getUnreceivedIndices(UUID uuid, int totalHints) {
        Set<Integer> received = receivedHints.getOrDefault(uuid, Set.of());
        List<Integer> unreceived = new ArrayList<>();
        for (int i = 0; i < totalHints; i++) {
            if (!received.contains(i)) unreceived.add(i);
        }
        return unreceived;
    }

    public int getNextRedeemIndex(UUID uuid, int totalHints) {
        Set<Integer> redeemed = redeemedHints.getOrDefault(uuid, Set.of());
        for (int i = 0; i < totalHints; i++) {
            if (!redeemed.contains(i)) {
                return i;
            }
        }
        return totalHints;
    }

    public boolean hasRedeemedAll(UUID uuid, int totalHints) {
        return getNextRedeemIndex(uuid, totalHints) >= totalHints;
    }

    public void resetAll() {
        receivedHints.clear();
        redeemedHints.clear();
        save();
    }

    private static Set<Integer> readIndexSet(JsonObject object, String key) {
        Set<Integer> indices = new HashSet<>();
        if (!object.has(key) || !object.get(key).isJsonArray()) {
            return indices;
        }
        for (JsonElement e : object.getAsJsonArray(key)) {
            indices.add(e.getAsInt());
        }
        return indices;
    }

    private static JsonArray toJsonArray(Set<Integer> values) {
        JsonArray array = new JsonArray();
        for (int value : values) {
            array.add(value);
        }
        return array;
    }
}
