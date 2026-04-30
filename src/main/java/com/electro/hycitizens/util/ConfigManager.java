package com.electro.hycitizens.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.PlayerSkin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class ConfigManager {
    private final Path configFile;
    private final Gson gson;
    private Map<String, Object> config;
    private boolean deferSave = false;
    private boolean dirty = false;

    public ConfigManager(@Nonnull Path pluginDataFolder) {
        this.configFile = pluginDataFolder.resolve("data.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.config = new LinkedHashMap<>();
        loadConfig();
    }

    public synchronized void beginBatch() {
        this.deferSave = true;
        this.dirty = false;
    }

    public synchronized void endBatch() {
        this.deferSave = false;
        if (this.dirty) {
            saveConfig();
            this.dirty = false;
        }
    }

    public synchronized void loadConfig() {
        if (!Files.exists(configFile)) {
            setDefaults();
            saveConfig();
            return;
        }

        try {
            JsonObject jsonObject = parseLenientJsonObject(configFile);
            config = gson.fromJson(jsonObject, LinkedHashMap.class);
            if (config == null) {
                config = new LinkedHashMap<>();
            }

            // Migrate old flat-key format to nested format
            if (needsMigration()) {
                migrateToNested();
                saveConfig();
            }
        } catch (IOException | JsonSyntaxException | IllegalStateException e) {
            getLogger().atInfo().log("Failed to load config: " + e.getMessage());
            setDefaults();
        }
    }

    @Nonnull
    private JsonObject parseLenientJsonObject(@Nonnull Path path) throws IOException {
        try (Reader fileReader = Files.newBufferedReader(path, StandardCharsets.UTF_8);
             JsonReader jsonReader = new JsonReader(fileReader)) {
            jsonReader.setStrictness(Strictness.LENIENT);
            JsonElement parsed = JsonParser.parseReader(jsonReader);
            if (!parsed.isJsonObject()) {
                throw new JsonSyntaxException("Expected root JSON object in " + path.getFileName());
            }
            return parsed.getAsJsonObject();
        }
    }

    private boolean needsMigration() {
        for (String key : config.keySet()) {
            if (key.contains(".")) {
                return true;
            }
        }
        return false;
    }

    private void migrateToNested() {
        Map<String, Object> newConfig = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : config.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (key.contains(".")) {
                setNestedValue(newConfig, key, value);
            } else {
                newConfig.put(key, value);
            }
        }

        config = newConfig;
    }

    @SuppressWarnings("unchecked")
    private synchronized void setNestedValue(@Nonnull Map<String, Object> root, @Nonnull String path, @Nullable Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = current.get(parts[i]);
            if (existing instanceof Map) {
                current = (Map<String, Object>) existing;
            } else {
                Map<String, Object> newMap = new LinkedHashMap<>();
                current.put(parts[i], newMap);
                current = newMap;
            }
        }

        String finalKey = parts[parts.length - 1];
        if (value == null) {
            current.remove(finalKey);
        } else {
            current.put(finalKey, value);
        }
    }

    @SuppressWarnings("unchecked")
    @Nullable
    private synchronized Object getNestedValue(@Nonnull String path) {
        String[] parts = path.split("\\.");
        Object current = config;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }

        return current;
    }

    @SuppressWarnings("unchecked")
    private synchronized void removeNestedValue(@Nonnull String path) {
        String[] parts = path.split("\\.");
        if (parts.length == 1) {
            config.remove(parts[0]);
            return;
        }

        // Walk to the parent of the target
        List<Map<String, Object>> parents = new ArrayList<>();
        parents.add(config);
        Map<String, Object> current = config;

        for (int i = 0; i < parts.length - 1; i++) {
            Object existing = current.get(parts[i]);
            if (existing instanceof Map) {
                current = (Map<String, Object>) existing;
                parents.add(current);
            } else {
                return; // Path doesn't exist
            }
        }

        // Remove the target key
        current.remove(parts[parts.length - 1]);

        // Clean up empty parent maps (walk backwards)
        for (int i = parts.length - 2; i >= 0; i--) {
            Map<String, Object> parent = parents.get(i);
            Map<String, Object> child = parents.get(i + 1);
            if (child.isEmpty()) {
                parent.remove(parts[i]);
            } else {
                break;
            }
        }
    }

    public synchronized void saveConfig() {
        try {
            Files.createDirectories(configFile.getParent());

            Path tempFile = configFile.getParent().resolve("data.json.tmp");
            try (Writer writer = Files.newBufferedWriter(tempFile)) {
                gson.toJson(config, writer);
            }

            Files.move(
                    tempFile,
                    configFile,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );

        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    private synchronized void conditionalSave() {
        if (deferSave) {
            dirty = true;
        } else {
            saveConfig();
        }
    }

    @Nullable
    public synchronized Object get(@Nonnull String path) {
        return getNestedValue(path);
    }

    @Nonnull
    public synchronized Object get(@Nonnull String path, @Nonnull Object defaultValue) {
        Object value = getNestedValue(path);
        return value != null ? value : defaultValue;
    }

    @Nullable
    public synchronized String getString(@Nonnull String path) {
        Object value = get(path);
        return value != null ? value.toString() : null;
    }

    @Nonnull
    public synchronized String getString(@Nonnull String path, @Nullable String defaultValue) {
        String value = getString(path);
        return value != null ? value : defaultValue;
    }

    public synchronized int getInt(@Nonnull String path, int defaultValue) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public synchronized float getFloat(@Nonnull String path, float defaultValue) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }

    public synchronized boolean getBoolean(@Nonnull String path, boolean defaultValue) {
        Object value = get(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    public synchronized long getLong(@Nonnull String path, long defaultValue) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }

    public synchronized double getDouble(@Nonnull String path, double defaultValue) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }

    @Nullable
    public synchronized Vector3f getVector3f(@Nonnull String path) {
        Object value = get(path);
        if (!(value instanceof Map<?, ?> map)) return null;

        try {
            float x = ((Number) map.get("x")).floatValue();
            float y = ((Number) map.get("y")).floatValue();
            float z = ((Number) map.get("z")).floatValue();
            return new Vector3f(x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void setVector3f(@Nonnull String path, @Nullable Vector3f vec) {
        if (vec == null) {
            removeNestedValue(path);
            conditionalSave();
            return;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", vec.x);
        map.put("y", vec.y);
        map.put("z", vec.z);

        setNestedValue(config, path, map);
        conditionalSave();
    }

    @Nullable
    public synchronized Vector3d getVector3d(@Nonnull String path) {
        Object value = get(path);
        if (!(value instanceof Map<?, ?> map)) return null;

        try {
            double x = ((Number) map.get("x")).doubleValue();
            double y = ((Number) map.get("y")).doubleValue();
            double z = ((Number) map.get("z")).doubleValue();
            return new Vector3d(x, y, z);
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void setVector3d(@Nonnull String path, @Nullable Vector3d vec) {
        if (vec == null) {
            removeNestedValue(path);
            conditionalSave();
            return;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("x", vec.x);
        map.put("y", vec.y);
        map.put("z", vec.z);

        setNestedValue(config, path, map);
        conditionalSave();
    }

    @Nullable
    public synchronized UUID getUUID(@Nonnull String path) {
        Object value = get(path);
        if (!(value instanceof String str)) return null;

        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @Nullable
    public synchronized List<UUID> getUUIDList(@Nonnull String path) {
        Object value = get(path);
        if (value == null) return null;

        List<UUID> uuids = new ArrayList<>();

        if (value instanceof List<?> list) {
            for (Object obj : list) {
                if (!(obj instanceof String str)) continue;

                try {
                    uuids.add(UUID.fromString(str));
                } catch (IllegalArgumentException ignored) {
                }
            }
        } else if (value instanceof String str) {
            // Backwards compatibility
            try {
                uuids.add(UUID.fromString(str));
            } catch (IllegalArgumentException ignored) {
            }
        }

        return uuids.isEmpty() ? null : uuids;
    }

    public synchronized void setUUID(@Nonnull String path, @Nullable UUID uuid) {
        if (uuid == null) {
            removeNestedValue(path);
        } else {
            setNestedValue(config, path, uuid.toString());
        }
        conditionalSave();
    }

    public synchronized void setUUIDList(@Nonnull String path, @Nullable List<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            removeNestedValue(path);
        } else {
            List<String> uuidStrings = new ArrayList<>();
            for (UUID uuid : uuids) {
                if (uuid != null) {
                    uuidStrings.add(uuid.toString());
                }
            }

            if (uuidStrings.isEmpty()) {
                removeNestedValue(path);
            } else {
                setNestedValue(config, path, uuidStrings);
            }
        }

        conditionalSave();
    }

    public synchronized void set(@Nonnull String path, @Nullable Object value) {
        if (value == null) {
            removeNestedValue(path);
        } else {
            setNestedValue(config, path, value);
        }
        conditionalSave();
    }

    private synchronized void setDefaults() {
        config.clear();
    }

    public synchronized void reload() {
        loadConfig();
    }

    @Nonnull
    public synchronized Map<String, Object> getAll() {
        return new LinkedHashMap<>(config);
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    public synchronized Set<String> getKeys(@Nonnull String path) {
        Object value = getNestedValue(path);
        if (value instanceof Map) {
            return new LinkedHashSet<>(((Map<String, Object>) value).keySet());
        }
        return Collections.emptySet();
    }

    @Nullable
    public synchronized PlayerSkin getPlayerSkin(@Nonnull String path) {
        Object value = get(path);
        if (!(value instanceof Map<?, ?> map)) return null;

        try {
            return new PlayerSkin(
                    (String) map.get("bodyCharacteristic"),
                    (String) map.get("underwear"),
                    (String) map.get("face"),
                    (String) map.get("eyes"),
                    (String) map.get("ears"),
                    (String) map.get("mouth"),
                    (String) map.get("facialHair"),
                    (String) map.get("haircut"),
                    (String) map.get("eyebrows"),
                    (String) map.get("pants"),
                    (String) map.get("overpants"),
                    (String) map.get("undertop"),
                    (String) map.get("overtop"),
                    (String) map.get("shoes"),
                    (String) map.get("headAccessory"),
                    (String) map.get("faceAccessory"),
                    (String) map.get("earAccessory"),
                    (String) map.get("skinFeature"),
                    (String) map.get("gloves"),
                    (String) map.get("cape")
            );
        } catch (Exception e) {
            return null;
        }
    }

    public synchronized void setPlayerSkin(@Nonnull String path, @Nullable PlayerSkin skin) {
        if (skin == null) {
            removeNestedValue(path);
            conditionalSave();
            return;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bodyCharacteristic", skin.bodyCharacteristic);
        map.put("underwear", skin.underwear);
        map.put("face", skin.face);
        map.put("eyes", skin.eyes);
        map.put("ears", skin.ears);
        map.put("mouth", skin.mouth);
        map.put("facialHair", skin.facialHair);
        map.put("haircut", skin.haircut);
        map.put("eyebrows", skin.eyebrows);
        map.put("pants", skin.pants);
        map.put("overpants", skin.overpants);
        map.put("undertop", skin.undertop);
        map.put("overtop", skin.overtop);
        map.put("shoes", skin.shoes);
        map.put("headAccessory", skin.headAccessory);
        map.put("faceAccessory", skin.faceAccessory);
        map.put("earAccessory", skin.earAccessory);
        map.put("skinFeature", skin.skinFeature);
        map.put("gloves", skin.gloves);
        map.put("cape", skin.cape);

        setNestedValue(config, path, map);
        conditionalSave();
    }

    @Nullable
    public synchronized List<String> getStringList(@Nonnull String path) {
        Object value = get(path);
        if (value == null) return null;

        List<String> strings = new ArrayList<>();

        if (value instanceof List<?> list) {
            for (Object obj : list) {
                if (obj != null) {
                    strings.add(obj.toString());
                }
            }
        }

        return strings.isEmpty() ? null : strings;
    }

    public synchronized void setStringList(@Nonnull String path, @Nullable List<String> strings) {
        if (strings == null || strings.isEmpty()) {
            removeNestedValue(path);
        } else {
            setNestedValue(config, path, new ArrayList<>(strings));
        }
        conditionalSave();
    }
}
