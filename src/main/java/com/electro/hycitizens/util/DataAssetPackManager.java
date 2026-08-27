package com.electro.hycitizens.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.Strictness;
import com.google.gson.stream.JsonReader;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.common.plugin.PluginManifest;
import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import com.hypixel.hytale.server.core.asset.AssetModule;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Locale;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;
import com.electro.hycitizens.map.CitizenMapMarkerAsset;

public class DataAssetPackManager {
    public static final Path DATA_PACK_PATH = Paths.get("mods", "HyCitizensData");
    public static final Path GENERATED_ROLES_PATH = DATA_PACK_PATH.resolve(Paths.get("Server", "NPC", "Roles"));
    public static final Path GENERATED_ATTITUDE_ROLES_PATH = DATA_PACK_PATH.resolve(Paths.get("Server", "NPC", "Attitude", "Roles"));
    public static final Path GENERATED_NPC_GROUPS_PATH = DATA_PACK_PATH.resolve(Paths.get("Server", "NPC", "Groups"));

    private static final Path LEGACY_ASSET_PACK_PATH = Paths.get("mods", "HyCitizensRoles");
    private static final Path MIGRATION_CONFLICTS_PATH = DATA_PACK_PATH.resolve("MigrationConflicts");

    public static boolean setup() {
        Path configPath = Paths.get("config.json");

        try {
            // Migrate from legacy HyCitizensRoles folder
            if (Files.exists(LEGACY_ASSET_PACK_PATH)) {
                Files.createDirectories(DATA_PACK_PATH);
                migrateLegacyAssetPack();
            }

            // Check if old data pack structure exists and migrate/cleanup
            if (Files.exists(DATA_PACK_PATH) && hasOldDataPackStructure()) {
                getLogger().atInfo().log("[HyCitizens] Detected old data pack structure - migrating to new system...");
                migrateFromOldDataPackStructure();
            }

            // Create manifest.json for HyCitizensData
            createManifest();

            // Try to register the pack
            if (!registerPackAtRuntime()) {
                if (Files.exists(configPath)) {
                    ensureDataPackInConfig(configPath);
                }
                getLogger().atWarning().log("[HyCitizens] HyCitizensData pack will be loaded on next server restart");
            } else {
                getLogger().atInfo().log("[HyCitizens] Successfully registered HyCitizensData pack at runtime");
            }
        } catch (IOException e) {
            getLogger().atSevere().log("Could not complete HyCitizens migration. " + e.getMessage());
        }
        return false;
    }

    private static void migrateLegacyAssetPack() throws IOException {
        Files.createDirectories(DATA_PACK_PATH);
        moveChildren(LEGACY_ASSET_PACK_PATH, DATA_PACK_PATH);
        deleteEmptyDirectories(LEGACY_ASSET_PACK_PATH);
        getLogger().atWarning().log("[HyCitizens] Migrated from legacy HyCitizensRoles folder.");
    }

    private static boolean hasOldDataPackStructure() {
        Path commonPath = DATA_PACK_PATH.resolve("Common");
        Path serverPath = DATA_PACK_PATH.resolve("Server");
        Path manifestPath = DATA_PACK_PATH.resolve("manifest.json");

        return Files.exists(commonPath) || Files.exists(serverPath) || Files.exists(manifestPath);
    }

    private static void migrateFromOldDataPackStructure() throws IOException {
        Path oldMapMarkersPath = DATA_PACK_PATH.resolve(Paths.get("Common", "UI", "WorldMap", "MapMarkers"));
        Path newMapMarkersPath = CitizenMapMarkerAsset.CUSTOM_MARKERS_PATH;

        // Migrate user custom markers
        if (Files.exists(oldMapMarkersPath) && Files.isDirectory(oldMapMarkersPath)) {
            Files.createDirectories(newMapMarkersPath);
            int migratedCount = 0;

            try (DirectoryStream<Path> stream = Files.newDirectoryStream(oldMapMarkersPath, "*.png")) {
                for (Path oldMarkerFile : stream) {
                    String fileName = oldMarkerFile.getFileName().toString();

                    // Skip built-in markers
                    if (fileName.toLowerCase(Locale.ROOT).startsWith("hycitizens-")) {
                        getLogger().atFine().log("[HyCitizens] Skipping built-in marker: " + fileName);
                        continue;
                    }

                    // Move user's custom marker to new location
                    Path newMarkerFile = newMapMarkersPath.resolve(fileName);
                    if (!Files.exists(newMarkerFile)) {
                        Files.copy(oldMarkerFile, newMarkerFile);
                        migratedCount++;
                        getLogger().atInfo().log("[HyCitizens] Migrated custom marker: " + fileName);
                    } else {
                        getLogger().atWarning().log("[HyCitizens] Custom marker already exists, skipping: " + fileName);
                    }
                }
            }

            if (migratedCount > 0) {
                getLogger().atInfo().log("[HyCitizens] Migrated " + migratedCount + " custom map marker(s) to new location");
            }
        }

        // Delete old data pack structure
        Path commonPath = DATA_PACK_PATH.resolve("Common");
        Path serverPath = DATA_PACK_PATH.resolve("Server");
        Path manifestPath = DATA_PACK_PATH.resolve("manifest.json");

        if (Files.exists(commonPath)) {
            deleteDirectoryRecursively(commonPath);
            getLogger().atInfo().log("[HyCitizens] Removed old 'Common' folder");
        }

        if (Files.exists(serverPath)) {
            deleteDirectoryRecursively(serverPath);
            getLogger().atInfo().log("[HyCitizens] Removed old 'Server' folder");
        }

        if (Files.exists(manifestPath)) {
            Files.delete(manifestPath);
            getLogger().atInfo().log("[HyCitizens] Removed old manifest.json");
        }

        getLogger().atInfo().log("[HyCitizens] Migration complete - now using programmatic asset registration!");
    }

    private static void deleteDirectoryRecursively(@Nonnull Path directory) throws IOException {
        if (!Files.exists(directory)) {
            return;
        }

        Files.walk(directory)
            .sorted(Comparator.reverseOrder())
            .forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    getLogger().atWarning().log("[HyCitizens] Failed to delete: " + path + " - " + e.getMessage());
                }
            });
    }

    private static void moveChildren(@Nonnull Path sourceDir, @Nonnull Path targetDir) throws IOException {
        if (!Files.isDirectory(sourceDir)) {
            return;
        }

        Files.createDirectories(targetDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(sourceDir)) {
            for (Path source : stream) {
                Path target = targetDir.resolve(source.getFileName().toString());
                if (Files.isDirectory(source)) {
                    if (Files.exists(target) && !Files.isDirectory(target)) {
                        Path conflictTarget = uniqueConflictPath(source);
                        Files.createDirectories(conflictTarget.getParent());
                        Files.move(source, conflictTarget);
                        getLogger().atWarning().log("[HyCitizens] Preserved a migration directory conflict at: " + conflictTarget);
                        continue;
                    }
                    moveChildren(source, target);
                    deleteEmptyDirectories(source);
                    continue;
                }

                if (!Files.exists(target)) {
                    Files.move(source, target);
                } else if (filesHaveSameBytes(source, target)) {
                    Files.delete(source);
                } else if ("manifest.json".equalsIgnoreCase(source.getFileName().toString())) {
                    Files.delete(source);
                } else {
                    Path conflictTarget = uniqueConflictPath(source);
                    Files.createDirectories(conflictTarget.getParent());
                    Files.move(source, conflictTarget);
                    getLogger().atWarning().log("[HyCitizens] Preserved a migration name conflict at: " + conflictTarget);
                }
            }
        }
    }

    @Nonnull
    private static Path uniqueConflictPath(@Nonnull Path source) {
        Path relative = LEGACY_ASSET_PACK_PATH.relativize(source);
        Path candidate = MIGRATION_CONFLICTS_PATH.resolve(relative).normalize();
        if (!Files.exists(candidate)) {
            return candidate;
        }

        String fileName = source.getFileName().toString();
        Path parent = candidate.getParent();
        for (int i = 1; i < 1000; i++) {
            Path numbered = parent.resolve(fileName + "." + i);
            if (!Files.exists(numbered)) {
                return numbered;
            }
        }
        return parent.resolve(fileName + "." + System.currentTimeMillis());
    }

    private static boolean filesHaveSameBytes(@Nonnull Path first, @Nonnull Path second) throws IOException {
        if (!Files.isRegularFile(first) || !Files.isRegularFile(second)) {
            return false;
        }
        return Files.mismatch(first, second) == -1L;
    }

    private static void deleteEmptyDirectories(@Nonnull Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            if (stream.iterator().hasNext()) {
                return;
            }
        }
        Files.delete(dir);
    }

    private static boolean registerPackAtRuntime() {
        try {
            AssetModule assetModule =
                AssetModule.get();

            if (assetModule == null) {
                getLogger().atWarning().log("[HyCitizens] AssetModule not available yet");
                return false;
            }

            // Check if already registered
            if (assetModule.getAssetPack("electro:HyCitizensData") != null) {
                getLogger().atInfo().log("[HyCitizens] HyCitizensData pack already registered");
                return true;
            }

            // Read the manifest
            Path manifestPath = DATA_PACK_PATH.resolve("manifest.json");
            if (!Files.exists(manifestPath)) {
                getLogger().atWarning().log("[HyCitizens] manifest.json does not exist yet");
                return false;
            }

            String manifestJson = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);
            char[] jsonChars = manifestJson.toCharArray();
            RawJsonReader reader =
                new RawJsonReader(jsonChars);

            ExtraInfo extraInfo = new ExtraInfo();
            PluginManifest manifest =
                PluginManifest.CODEC.decodeJson(reader, extraInfo);

            if (manifest == null) {
                getLogger().atWarning().log("[HyCitizens] Failed to decode manifest");
                return false;
            }

            try {
                Method[] methods = assetModule.getClass().getMethods();
                for (Method method : methods) {
                    if (!"registerPack".equals(method.getName())) {
                        continue;
                    }
                    Class<?>[] paramTypes = method.getParameterTypes();
                    if (paramTypes.length == 4 &&
                        paramTypes[0] == String.class &&
                        paramTypes[1] == Path.class &&
                        paramTypes[2] == PluginManifest.class) {

                        Object fourthParam;
                        if (paramTypes[3] == boolean.class || paramTypes[3] == Boolean.class) {
                            fourthParam = true;
                        } else {
                            try {
                                Object[] enumConstants = paramTypes[3].getEnumConstants();
                                fourthParam = (enumConstants != null && enumConstants.length > 0) ? enumConstants[0] : null;
                            } catch (Exception e) {
                                fourthParam = null;
                            }
                        }

                        if (fourthParam != null) {
                            method.invoke(assetModule, "electro:HyCitizensData", DATA_PACK_PATH, manifest, fourthParam);
                            getLogger().atInfo().log("[HyCitizens] Registered HyCitizensData pack at runtime");
                            return true;
                        }
                    }
                }
                getLogger().atWarning().log("[HyCitizens] Could not find compatible registerPack method");
                return false;
            } catch (IllegalStateException ise) {
                if (ise.getMessage() != null && ise.getMessage().contains("already exists")) {
                    getLogger().atInfo().log("[HyCitizens] HyCitizensData pack already registered (caught IllegalStateException)");
                    return true;
                }
                throw ise;
            }

        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Failed to register pack at runtime: " + e.getClass().getName() + ": " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private static void createManifest() throws IOException {
        Path manifestPath = DATA_PACK_PATH.resolve("manifest.json");
        if (Files.exists(manifestPath)) {
            return;
        }

        JsonObject manifest = new JsonObject();
        manifest.addProperty("Group", "electro");
        manifest.addProperty("Name", "HyCitizensData");
        manifest.addProperty("Description", "Dynamically generated assets for HyCitizens plugin");
        manifest.addProperty("Version", "1.0.0");
        manifest.addProperty("ServerVersion", ">=0.5.0-pre.9 <0.6.0");

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.write(manifestPath, gson.toJson(manifest).getBytes(StandardCharsets.UTF_8));
        getLogger().atInfo().log("[HyCitizens] Created manifest.json for HyCitizensData");
    }

    private static boolean ensureDataPackInConfig(Path configPath) throws IOException {
        JsonObject config;
        try {
            config = parseConfigJson(configPath);
        } catch (JsonSyntaxException | IllegalStateException e) {
            getLogger().atWarning().log("[HyCitizens] Could not parse config.json. " +
                    "Details: " + e.getMessage());
            return false;
        }

        JsonObject mods = config.has("Mods") && config.get("Mods").isJsonObject()
                ? config.getAsJsonObject("Mods")
                : new JsonObject();

        boolean changed = false;

        // Remove legacy pack if exists
        if (mods.has("electro:HyCitizensRoles")) {
            mods.remove("electro:HyCitizensRoles");
            changed = true;
        }

        // Ensure HyCitizensData is registered
        if (!mods.has("electro:HyCitizensData")) {
            mods.add("electro:HyCitizensData", new JsonObject());
            changed = true;
            getLogger().atInfo().log("[HyCitizens] Registered HyCitizensData mod pack in config.json");
        }

        if (changed) {
            config.add("Mods", mods);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.write(configPath, gson.toJson(config).getBytes(StandardCharsets.UTF_8));
        }

        return changed;
    }

    private static boolean removeDataPackFromConfig(Path configPath) throws IOException {
        JsonObject config;
        try {
            config = parseConfigJson(configPath);
        } catch (JsonSyntaxException | IllegalStateException e) {
            getLogger().atWarning().log("[HyCitizens] Could not parse config.json while removing data pack entry. " +
                    "The server config appears to contain malformed JSON, so HyCitizens will skip editing it. " +
                    "Details: " + e.getMessage());
            return false;
        }

        JsonObject mods = config.has("Mods") && config.get("Mods").isJsonObject()
                ? config.getAsJsonObject("Mods")
                : new JsonObject();

        boolean changed = false;

        // Remove legacy asset pack
        if (mods.has("electro:HyCitizensRoles")) {
            mods.remove("electro:HyCitizensRoles");
            changed = true;
            getLogger().atInfo().log("[HyCitizens] Removed legacy data pack from config.json");
        }

        // Remove current data pack
        if (mods.has("electro:HyCitizensData")) {
            mods.remove("electro:HyCitizensData");
            changed = true;
            getLogger().atInfo().log("[HyCitizens] Removed HyCitizensData pack from config.json");
        }

        if (changed) {
            config.add("Mods", mods);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.write(configPath, gson.toJson(config).getBytes(StandardCharsets.UTF_8));
        }

        return changed;
    }



    private static JsonObject parseConfigJson(Path configPath) throws IOException {
        String configContent = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        JsonReader reader = new JsonReader(new StringReader(configContent));
        reader.setStrictness(Strictness.LENIENT);

        JsonElement parsed = JsonParser.parseReader(reader);
        if (!parsed.isJsonObject()) {
            throw new JsonSyntaxException("Expected root JSON object in config.json.");
        }
        return parsed.getAsJsonObject();
    }
}
