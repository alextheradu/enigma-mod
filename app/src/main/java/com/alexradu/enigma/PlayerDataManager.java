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

    public PlayerDataManager(EnigmaMod mod) {
        Path dir = FabricLoader.getInstance().getConfigDir().resolve("enigma");
        this.dataPath = dir.resolve("data.json");
        load();
    }

    public void load() {
        receivedHints.clear();
        if (!Files.exists(dataPath)) return;
        try (Reader reader = Files.newBufferedReader(dataPath)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) return;
            for (var entry : root.entrySet()) {
                try {
                    UUID uuid = UUID.fromString(entry.getKey());
                    Set<Integer> indices = new HashSet<>();
                    for (JsonElement e : entry.getValue().getAsJsonArray()) {
                        indices.add(e.getAsInt());
                    }
                    receivedHints.put(uuid, indices);
                } catch (IllegalArgumentException ignored) {}
            }
        } catch (IOException e) {
            EnigmaMod.LOGGER.error("Failed to load player data", e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        for (var entry : receivedHints.entrySet()) {
            JsonArray arr = new JsonArray();
            for (int i : entry.getValue()) arr.add(i);
            root.add(entry.getKey().toString(), arr);
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

    public List<Integer> getUnreceivedIndices(UUID uuid, int totalHints) {
        Set<Integer> received = receivedHints.getOrDefault(uuid, Set.of());
        List<Integer> unreceived = new ArrayList<>();
        for (int i = 0; i < totalHints; i++) {
            if (!received.contains(i)) unreceived.add(i);
        }
        return unreceived;
    }
}
