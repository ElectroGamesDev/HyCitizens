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

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class RoleAssetPackManager {
    public static void setup() {
        Path rolesPath = Paths.get("mods", "HyCitizensRoles", "Server", "NPC", "Roles");
        Path manifestPath = Paths.get("mods", "HyCitizensRoles", "manifest.json");
        Path configPath = Paths.get("config.json");

        try {
            Files.createDirectories(rolesPath);

            // Check config.json and ensure the mod is registered if DefaultModsEnabled is false
            if (Files.exists(configPath)) {
                ensureRolePackEnabled(configPath);
            }

            if (!Files.exists(manifestPath)) {
                String manifestContent = "{\n" +
                        "  \"Group\": \"electro\",\n" +
                        "  \"Name\": \"HyCitizensRoles\",\n" +
                        "  \"Version\": \"1.0.0\",\n" +
                        "  \"ServerVersion\": \"2026.03.26-89796e57b\",\n" +
                        "  \"Description\": \"Generated asset pack for HyCitizens.\",\n" +
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
                        "  \"IncludesAssetPack\": false,\n" +
                        "  \"SubPlugins\": []\n" +
                        "}";

                Files.write(manifestPath, manifestContent.getBytes(StandardCharsets.UTF_8));

                getLogger().atWarning().log("================================================================================");
                getLogger().atWarning().log("                                                                                ");
                getLogger().atWarning().log("                      !!!  IMPORTANT NOTICE  !!!                               ");
                getLogger().atWarning().log("                                                                                ");
                getLogger().atWarning().log("          HYCITIZENS IS PERFORMING A ONE-TIME SHUTDOWN                          ");
                getLogger().atWarning().log("                                                                                ");
                getLogger().atWarning().log("    The server will now shut down automatically. This is expected              ");
                getLogger().atWarning().log("    and should only happen once.                                               ");
                getLogger().atWarning().log("                                                                                ");
                getLogger().atWarning().log("    Simply start the server again after shutdown completes.                    ");
                getLogger().atWarning().log("                                                                                ");
                getLogger().atWarning().log("================================================================================");

                HytaleServer.get().shutdownServer();
            }
            else {
                String content = new String(Files.readAllBytes(manifestPath), StandardCharsets.UTF_8);

                if (!content.contains("\"ServerVersion\": \"2026.03.26-89796e57b\"")) {
                    content = content.replaceAll(
                            "\"ServerVersion\":\\s*\"[^\"]*\"",
                            "\"ServerVersion\": \"2026.03.26-89796e57b\""
                    );
                    Files.write(manifestPath, content.getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (IOException e) {
            getLogger().atSevere().log("Could not create role asset pack manager. " + e.getMessage());
        }
    }

    private static void ensureRolePackEnabled(Path configPath) throws IOException {
        JsonObject config;
        try {
            config = parseConfigJson(configPath);
        } catch (JsonSyntaxException | IllegalStateException e) {
            getLogger().atWarning().log("[HyCitizens] Could not parse config.json while registering HyCitizensRoles. " +
                    "The server config appears to contain malformed JSON, so HyCitizens will skip editing it. " +
                    "Details: " + e.getMessage());
            return;
        }

        boolean defaultModsEnabled = config.has("DefaultModsEnabled")
                && config.get("DefaultModsEnabled").isJsonPrimitive()
                && config.get("DefaultModsEnabled").getAsBoolean();

        if (defaultModsEnabled) {
            return;
        }

        JsonObject mods = config.has("Mods") && config.get("Mods").isJsonObject()
                ? config.getAsJsonObject("Mods")
                : new JsonObject();

        if (mods.has("electro:HyCitizensRoles")) {
            return;
        }

        JsonObject modEntry = new JsonObject();
        modEntry.addProperty("Enabled", true);
        mods.add("electro:HyCitizensRoles", modEntry);
        config.add("Mods", mods);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        Files.write(configPath, gson.toJson(config).getBytes(StandardCharsets.UTF_8));
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
