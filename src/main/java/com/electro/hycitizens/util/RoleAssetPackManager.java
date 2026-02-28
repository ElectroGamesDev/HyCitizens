package com.electro.hycitizens.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.server.core.HytaleServer;

import java.io.IOException;
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
                String configContent = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
                JsonObject config = JsonParser.parseString(configContent).getAsJsonObject();

                boolean defaultModsEnabled = config.has("DefaultModsEnabled") && config.get("DefaultModsEnabled").getAsBoolean();

                if (!defaultModsEnabled) {
                    JsonObject mods = config.has("Mods") ? config.getAsJsonObject("Mods") : new JsonObject();

                    if (!mods.has("electro:HyCitizensRoles")) {
                        JsonObject modEntry = new JsonObject();
                        modEntry.addProperty("Enabled", true);
                        mods.add("electro:HyCitizensRoles", modEntry);
                        config.add("Mods", mods);

                        Gson gson = new GsonBuilder().setPrettyPrinting().create();
                        Files.write(configPath, gson.toJson(config).getBytes(StandardCharsets.UTF_8));
                    }
                }
            } else {
                getLogger().atWarning().log("config.json not found, skipping mod registration check.");
            }

            if (!Files.exists(manifestPath)) {
                String manifestContent = "{\n" +
                        "  \"Group\": \"electro\",\n" +
                        "  \"Name\": \"HyCitizensRoles\",\n" +
                        "  \"Version\": \"1.0.0\",\n" +
                        "  \"ServerVersion\": \"2026.02.19-1a311a592\",\n" +
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
        } catch (IOException e) {
            getLogger().atSevere().log("Could not create role asset pack manager. " + e.getMessage());
        }
    }
}
