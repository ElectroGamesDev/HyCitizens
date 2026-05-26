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

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class DataAssetPackManager {
    public static final Path DATA_PACK_PATH = Paths.get("mods", "HyCitizensData");
    public static final Path GENERATED_ROLES_PATH = DATA_PACK_PATH.resolve(Paths.get("Server", "NPC", "Roles"));
    public static final Path MAP_MARKERS_PATH = DATA_PACK_PATH.resolve(Paths.get("Common", "UI", "WorldMap", "MapMarkers"));

    private static final Path LEGACY_ASSET_PACK_PATH = Paths.get("mods", "HyCitizensRoles");
    private static final Path MIGRATION_CONFLICTS_PATH = DATA_PACK_PATH.resolve("MigrationConflicts");
    private static final String DATA_PACK_MOD_ID = "electro:HyCitizensData";
    private static final String LEGACY_ASSET_PACK_MOD_ID = "electro:HyCitizensRoles";
    private static final String SERVER_VERSION = ">=0.5.0-pre.9 <0.6.0";

    public static boolean setup() {
        Path manifestPath = DATA_PACK_PATH.resolve("manifest.json");
        Path configPath = Paths.get("config.json");
        boolean needsShutdown = false;

        try {
            Files.createDirectories(DATA_PACK_PATH);

            if (Files.exists(LEGACY_ASSET_PACK_PATH)) {
                migrateLegacyAssetPack();
                needsShutdown = true;
            }

            Files.createDirectories(GENERATED_ROLES_PATH);
            Files.createDirectories(MAP_MARKERS_PATH);

            if (Files.exists(configPath) && ensureDataPackEnabled(configPath)) {
                needsShutdown = true;
            }

            if (writeManifestIfNeeded(manifestPath).requiresShutdown()) {
                needsShutdown = true;
            }

            if (needsShutdown) {
                logRestartNotice();
                HytaleServer.get().shutdownServer();
                return true;
            }
        } catch (IOException e) {
            getLogger().atSevere().log("Could not set up HyCitizensData asset pack. " + e.getMessage());
        }
        return false;
    }

    private static void migrateLegacyAssetPack() throws IOException {
        Files.createDirectories(DATA_PACK_PATH);
        moveChildren(LEGACY_ASSET_PACK_PATH, DATA_PACK_PATH);
        deleteEmptyDirectories(LEGACY_ASSET_PACK_PATH);
        getLogger().atWarning().log("[HyCitizens] Migrated generated roles and marker assets into HyCitizensData.");
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

    private static boolean ensureDataPackEnabled(Path configPath) throws IOException {
        JsonObject config;
        try {
            config = parseConfigJson(configPath);
        } catch (JsonSyntaxException | IllegalStateException e) {
            getLogger().atWarning().log("[HyCitizens] Could not parse config.json while registering HyCitizensData. " +
                    "The server config appears to contain malformed JSON, so HyCitizens will skip editing it. " +
                    "Details: " + e.getMessage());
            return false;
        }

        boolean defaultModsEnabled = config.has("DefaultModsEnabled")
                && config.get("DefaultModsEnabled").isJsonPrimitive()
                && config.get("DefaultModsEnabled").getAsBoolean();

        JsonObject mods = config.has("Mods") && config.get("Mods").isJsonObject()
                ? config.getAsJsonObject("Mods")
                : new JsonObject();

        boolean changed = false;
        if (mods.has(LEGACY_ASSET_PACK_MOD_ID)) {
            mods.remove(LEGACY_ASSET_PACK_MOD_ID);
            changed = true;
        }

        if (!defaultModsEnabled) {
            JsonObject modEntry = mods.has(DATA_PACK_MOD_ID) && mods.get(DATA_PACK_MOD_ID).isJsonObject()
                    ? mods.getAsJsonObject(DATA_PACK_MOD_ID)
                    : new JsonObject();
            if (!modEntry.has("Enabled")
                    || !modEntry.get("Enabled").isJsonPrimitive()
                    || !modEntry.get("Enabled").getAsBoolean()) {
                modEntry.addProperty("Enabled", true);
                mods.add(DATA_PACK_MOD_ID, modEntry);
                changed = true;
            }
        }

        if (changed) {
            config.add("Mods", mods);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.write(configPath, gson.toJson(config).getBytes(StandardCharsets.UTF_8));
        }

        return changed;
    }

    private enum ManifestUpdateResult {
        UNCHANGED(false),
        SERVER_VERSION_ONLY(false),
        RESTART_REQUIRED(true);

        private final boolean requiresShutdown;

        ManifestUpdateResult(boolean requiresShutdown) {
            this.requiresShutdown = requiresShutdown;
        }

        private boolean requiresShutdown() {
            return requiresShutdown;
        }
    }

    @Nonnull
    private static ManifestUpdateResult writeManifestIfNeeded(@Nonnull Path manifestPath) throws IOException {
        String desired = manifestContent();
        if (Files.exists(manifestPath)) {
            String existing = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);
            if (existing.equals(desired)) {
                return ManifestUpdateResult.UNCHANGED;
            }
            if (onlyServerVersionChanged(existing, desired)) {
                Files.write(manifestPath, desired.getBytes(StandardCharsets.UTF_8));
                return ManifestUpdateResult.SERVER_VERSION_ONLY;
            }
        }

        Files.write(manifestPath, desired.getBytes(StandardCharsets.UTF_8));
        return ManifestUpdateResult.RESTART_REQUIRED;
    }

    private static boolean onlyServerVersionChanged(@Nonnull String existing, @Nonnull String desired) {
        try {
            JsonElement existingJson = JsonParser.parseString(existing);
            JsonElement desiredJson = JsonParser.parseString(desired);
            if (!existingJson.isJsonObject() || !desiredJson.isJsonObject()) {
                return false;
            }

            JsonObject existingObject = existingJson.getAsJsonObject();
            JsonObject desiredObject = desiredJson.getAsJsonObject();
            JsonElement existingServerVersion = existingObject.remove("ServerVersion");
            JsonElement desiredServerVersion = desiredObject.remove("ServerVersion");

            return existingServerVersion != null
                    && desiredServerVersion != null
                    && existingObject.equals(desiredObject);
        } catch (JsonSyntaxException | IllegalStateException e) {
            return false;
        }
    }

    private static String manifestContent() {
        return "{\n" +
                "  \"Group\": \"electro\",\n" +
                "  \"Name\": \"HyCitizensData\",\n" +
                "  \"Version\": \"1.0.0\",\n" +
                "  \"ServerVersion\": \"" + SERVER_VERSION + "\",\n" +
                "  \"Description\": \"Generated data and asset pack for HyCitizens.\",\n" +
                "  \"Authors\": [\n" +
                "    {\n" +
                "      \"Name\": \"Electro\",\n" +
                "      \"Url\": \"https://github.com/ElectroGamesDev\"\n" +
                "    }\n" +
                "  ],\n" +
                "  \"Website\": \"https://github.com/ElectroGamesDev\",\n" +
                "  \"Dependencies\": {},\n" +
                "  \"OptionalDependencies\": {},\n" +
                "  \"LoadBefore\": {},\n" +
                "  \"DisabledByDefault\": false,\n" +
                "  \"IncludesAssetPack\": true,\n" +
                "  \"SubPlugins\": []\n" +
                "}";
    }

    private static void logRestartNotice() {
        getLogger().atWarning().log("================================================================================");
        getLogger().atWarning().log("                                                                                ");
        getLogger().atWarning().log("                      !!!  IMPORTANT NOTICE  !!!                               ");
        getLogger().atWarning().log("                                                                                ");
        getLogger().atWarning().log("          HYCITIZENS IS PERFORMING A ONE-TIME DATA MIGRATION                    ");
        getLogger().atWarning().log("                                                                                ");
        getLogger().atWarning().log("    Generated roles, marker images, and the asset-pack manifest now live in      ");
        getLogger().atWarning().log("    mods/HyCitizensData.                                                        ");
        getLogger().atWarning().log("                                                                                ");
        getLogger().atWarning().log("    The server will now shut down automatically. This is expected after          ");
        getLogger().atWarning().log("    updating, and should only happen once. Start the server again after          ");
        getLogger().atWarning().log("    shutdown completes.                                                         ");
        getLogger().atWarning().log("                                                                                ");
        getLogger().atWarning().log("================================================================================");
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
