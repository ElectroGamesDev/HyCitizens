package com.electro.hycitizens.managers;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.api.scripting.ScriptBlock;
import com.electro.hycitizens.models.CitizenData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class ScriptEditorManager {

    private static ScriptEditorManager instance;

    private final Gson gson = new GsonBuilder().create();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    // token → session
    private final Map<String, PendingEditSession> sessions = new ConcurrentHashMap<>();

    public static ScriptEditorManager get() {
        if (instance == null) {
            instance = new ScriptEditorManager();
        }
        return instance;
    }

    private ScriptEditorManager() {}

    // Public API
    public void startEditSession(PlayerRef playerRef, String citizenId) {
        CitizenData citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
        if (citizen == null) {
            playerRef.sendMessage(Message.raw("[HyCitizens] No citizen found with ID: " + citizenId).color(Color.RED));
            return;
        }

        String token = UUID.randomUUID().toString();
        String secret = getSecret();
        String baseUrl = getBaseUrl();
        long timestamp = System.currentTimeMillis() / 1000L;

        List<ScriptBlock> scripts = citizen.getScripts();
        if (scripts == null) scripts = new ArrayList<>();
        String scriptsJson = gson.toJson(scripts);

        String hmacInput = token + ":" + citizenId + ":" + timestamp;
        String hmac = computeHmac(hmacInput, secret);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("citizen_id", citizenId);
        payload.put("citizen_name", citizen.getName() != null ? citizen.getName() : citizenId);
        payload.put("scripts_json", scriptsJson);
        payload.put("timestamp", timestamp);
        payload.put("hmac", hmac);

        String payloadJson = gson.toJson(payload);
        String uploadUrl = baseUrl + "/api/script-editor/upload.php";

        // Store the session before the async call so it's ready when save is called
        PendingEditSession session = new PendingEditSession(citizenId, token, System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3));
        sessions.put(token, session);

        playerRef.sendMessage(Message.raw("[HyCitizens] Uploading scripts...").color(new Color(0xA0A0B0)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        String editorUrl = baseUrl + "/editor/?token=" + token;
                        String saveCommand = "/hc script save " + token;

                        playerRef.sendMessage(
                            Message.raw("[HyCitizens] ").color(new Color(0x00D4FF))
                                .insert(Message.raw("Edit scripts for ").color(Color.WHITE))
                                .insert(Message.raw(citizen.getName() != null ? citizen.getName() : citizenId).color(new Color(0x00D4FF)))
                                .insert(Message.raw(" → ").color(new Color(0xA0A0B0)))
                                .insert(Message.raw("[Open Editor]").color(new Color(0x8B5CF6)).link(editorUrl))
                        );
                        playerRef.sendMessage(
                            Message.raw("[HyCitizens] ").color(new Color(0x00D4FF))
                                .insert(Message.raw("When done, run: ").color(new Color(0xA0A0B0)))
                                .insert(Message.raw(saveCommand).color(Color.WHITE))
                        );
                        playerRef.sendMessage(
                            Message.raw("[HyCitizens] Session expires in 3 hours.").color(new Color(0xA0A0B0))
                        );
                    } else {
                        sessions.remove(token);
                        getLogger().atWarning().log("[HyCitizens] ScriptEditor upload failed (HTTP " + response.statusCode() + ") for citizen " + citizenId + ". Response: " + response.body());
                        playerRef.sendMessage(Message.raw("[HyCitizens] Could not start the editor session. Please check console for more details.").color(Color.RED));
                    }
                })
                .exceptionally(ex -> {
                    sessions.remove(token);
                    getLogger().atWarning().log("[HyCitizens] ScriptEditor upload error for citizen " + citizenId + ": " + ex.getMessage());
                    playerRef.sendMessage(Message.raw("[HyCitizens] Could not reach the script editor. Please try again later.").color(Color.RED));
                    return null;
                });
    }

    public void applyEditSession(PlayerRef playerRef, String token, Store<EntityStore> store) {
        // Validate token format (UUID) to prevent path traversal on the server side
        if (!token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            playerRef.sendMessage(Message.raw("[HyCitizens] Invalid token format.").color(Color.RED));
            return;
        }

        PendingEditSession session = sessions.get(token);
        if (session == null) {
            playerRef.sendMessage(Message.raw("[HyCitizens] Unknown token. Start a new session with /hc script edit <id>.").color(Color.RED));
            return;
        }
        if (System.currentTimeMillis() > session.expiryMillis) {
            sessions.remove(token);
            playerRef.sendMessage(Message.raw("[HyCitizens] Session expired. Start a new one with /hc script edit <id>.").color(Color.RED));
            return;
        }

        CitizenData citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(session.citizenId);
        if (citizen == null) {
            sessions.remove(token);
            playerRef.sendMessage(Message.raw("[HyCitizens] Citizen no longer exists: " + session.citizenId).color(Color.RED));
            return;
        }

        playerRef.sendMessage(Message.raw("[HyCitizens] Fetching scripts...").color(new Color(0xA0A0B0)));

        String fetchUrl = getBaseUrl() + "/api/script-editor/fetch.php?token=" + token;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fetchUrl))
                .GET()
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 404) {
                        playerRef.sendMessage(Message.raw("[HyCitizens] Session not found on server (may have expired). Start a new one.").color(Color.RED));
                        sessions.remove(token);
                        return;
                    }
                    if (response.statusCode() != 200) {
                        playerRef.sendMessage(Message.raw("[HyCitizens] Fetch failed (HTTP " + response.statusCode() + ").").color(Color.RED));
                        return;
                    }

                    try {
                        // fetch.php returns {citizen_id, citizen_name, scripts:[…]}
                        com.google.gson.JsonObject root = gson.fromJson(response.body(), com.google.gson.JsonObject.class);
                        com.google.gson.JsonArray scriptsArray = root.has("scripts") ? root.getAsJsonArray("scripts") : root.getAsJsonArray();
                        List<ScriptBlock> parsed = gson.fromJson(scriptsArray, new TypeToken<List<ScriptBlock>>(){}.getType());
                        if (parsed == null) parsed = new ArrayList<>();

                        List<ScriptBlock> finalParsed = parsed;
                        World world = store.getExternalData().getWorld();
                        world.execute(() -> {
                            try {
                                citizen.setScripts(finalParsed);
                                HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                                // sessions.remove(token);
                                int count = finalParsed.size();
                                playerRef.sendMessage(
                                    Message.raw("[HyCitizens] ").color(new Color(0x00D4FF))
                                        .insert(Message.raw("Finished fetching and applied scripts for ").color(Color.WHITE))
                                        .insert(Message.raw(citizen.getName() != null ? citizen.getName() : session.citizenId).color(new Color(0x00D4FF)))
                                        .insert(Message.raw(". (" + count + " script" + (count != 1 ? "s" : "") + " loaded)").color(Color.WHITE))
                                );
                            } catch (Exception e) {
                                getLogger().atWarning().log("[HyCitizens] ScriptEditor apply error for token " + token + ": " + e.getMessage());
                                playerRef.sendMessage(Message.raw("[HyCitizens] Fetched scripts, but applying them failed. Check console.").color(Color.RED));
                            }
                        });
                    } catch (Exception e) {
                        getLogger().atWarning().log("[HyCitizens] ScriptEditor parse error for token " + token + ": " + e.getMessage());
                        playerRef.sendMessage(Message.raw("[HyCitizens] Failed to parse scripts. The JSON may be malformed.").color(Color.RED));
                    }
                })
                .exceptionally(ex -> {
                    getLogger().atWarning().log("[HyCitizens] ScriptEditor fetch error for token " + token + ": " + ex.getMessage());
                    playerRef.sendMessage(Message.raw("[HyCitizens] Could not reach the script editor. Please try again later.").color(Color.RED));
                    return null;
                });
    }

    // Helpers

    private String getSecret() {
        return "HyCitizensPlugin";
    }

    private String getBaseUrl() {
        String url = HyCitizensPlugin.get().getConfigManager().getString("script_editor_url", "https://hycitizens.com");
        // Strip trailing slash
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String computeHmac(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : raw) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] HMAC computation failed: " + e.getMessage());
            return "";
        }
    }

    // Inner types

    private static class PendingEditSession {
        final String citizenId;
        final String token;
        final long expiryMillis;

        PendingEditSession(String citizenId, String token, long expiryMillis) {
            this.citizenId = citizenId;
            this.token = token;
            this.expiryMillis = expiryMillis;
        }
    }
}
