package com.electro.hycitizens.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3f;
import com.hypixel.hytale.protocol.PlayerSkin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ConfigManager {
    private final Path configFile;
    private final Gson gson;
    private Map<String, Object> config;

    public ConfigManager(@Nonnull Path pluginDataFolder) {
        this.configFile = pluginDataFolder.resolve("data.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.config = new HashMap<>();
        loadConfig();
    }

    public void loadConfig() {
        if (!Files.exists(configFile)) {
            // Create default config
            setDefaults();
            saveConfig();
            return;
        }

        try (Reader reader = new FileReader(configFile.toFile())) {
            JsonObject jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
            config = gson.fromJson(jsonObject, Map.class);
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
            setDefaults();
        }
    }

    public void saveConfig() {
        try {
            // Ensure parent directories exist
            Files.createDirectories(configFile.getParent());

            try (Writer writer = new FileWriter(configFile.toFile())) {
                gson.toJson(config, writer);
            }
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }

    @Nullable
    public Object get(@Nonnull String path) {
        return config.get(path);
    }

    @Nonnull
    public Object get(@Nonnull String path, @Nonnull Object defaultValue) {
        return config.getOrDefault(path, defaultValue);
    }

    @Nullable
    public String getString(@Nonnull String path) {
        Object value = get(path);
        return value != null ? value.toString() : null;
    }

    @Nonnull
    public String getString(@Nonnull String path, @Nullable String defaultValue) {
        String value = getString(path);
        return value != null ? value : defaultValue;
    }

    public int getInt(@Nonnull String path, int defaultValue) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    public float getFloat(@Nonnull String path, float defaultValue) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return defaultValue;
    }

    public boolean getBoolean(@Nonnull String path, boolean defaultValue) {
        Object value = get(path);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }

    @Nullable
    public Vector3f getVector3f(@Nonnull String path) {
        Object value = config.get(path);
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

    public void setVector3f(@Nonnull String path, @Nullable Vector3f vec) {
        if (vec == null) {
            config.remove(path);
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("x", vec.x);
        map.put("y", vec.y);
        map.put("z", vec.z);

        config.put(path, map);
        saveConfig();
    }

    @Nullable
    public Vector3d getVector3d(@Nonnull String path) {
        Object value = config.get(path);
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

    public void setVector3d(@Nonnull String path, @Nullable Vector3d vec) {
        if (vec == null) {
            config.remove(path);
            return;
        }

        Map<String, Object> map = new HashMap<>();
        map.put("x", vec.x);
        map.put("y", vec.y);
        map.put("z", vec.z);

        config.put(path, map);
        saveConfig();
    }

    @Nullable
    public UUID getUUID(@Nonnull String path) {
        Object value = config.get(path);
        if (!(value instanceof String str)) return null;

        try {
            return UUID.fromString(str);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void setUUID(@Nonnull String path, @Nullable UUID uuid) {
        if (uuid == null) {
            config.remove(path);
        } else {
            config.put(path, uuid.toString());
        }
        saveConfig();
    }

    public void set(@Nonnull String path, @Nullable Object value) {
        if (value == null) {
            config.remove(path);
        } else {
            config.put(path, value);
        }
        saveConfig();
    }

    private void setDefaults() {
        config.clear();
    }

    public void reload() {
        loadConfig();
    }

    @Nonnull
    public Map<String, Object> getAll() {
        return new HashMap<>(config);
    }

    @Nullable
    public PlayerSkin getPlayerSkin(@Nonnull String path) {
        Object value = config.get(path);
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

    public void setPlayerSkin(@Nonnull String path, @Nullable PlayerSkin skin) {
        if (skin == null) {
            config.remove(path);
            saveConfig();
            return;
        }

        Map<String, Object> map = new HashMap<>();
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

        config.put(path, map);
        saveConfig();
    }

    public long getLong(@Nonnull String path, long defaultValue) {
        Object value = get(path);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
}
