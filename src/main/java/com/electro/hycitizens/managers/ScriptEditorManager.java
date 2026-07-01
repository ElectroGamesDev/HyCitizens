package com.electro.hycitizens.managers;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.api.scripting.ScriptBlock;
import com.electro.hycitizens.models.CitizenData;
import com.electro.hycitizens.util.ResourceId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.awt.Color;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class ScriptEditorManager {

    private static ScriptEditorManager instance;

    private final Gson gson = new GsonBuilder().create();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // token to session
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
        if (!playerRef.hasPermission("hycitizens.admin")) {
            playerRef.sendMessage(Message.raw("[HyCitizens] You do not have permission to edit scripts.").color(Color.RED));
            return;
        }
        if (!ResourceId.isValid(citizenId)) {
            playerRef.sendMessage(Message.raw("[HyCitizens] Invalid citizen ID.").color(Color.RED));
            return;
        }
        CitizenData citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(citizenId);
        if (citizen == null) {
            playerRef.sendMessage(Message.raw("[HyCitizens] No citizen found with ID: " + citizenId).color(Color.RED));
            return;
        }

        String token = UUID.randomUUID().toString();
        String baseUrl = getBaseUrl();

        List<ScriptBlock> scripts = citizen.getScripts();
        if (scripts == null) scripts = new ArrayList<>();
        String scriptsJson = gson.toJson(scripts);
        String citizenName = citizen.getName() != null ? citizen.getName() : citizenId;
        String payloadSha256 = sha256(scriptsJson + "\n" + citizenName);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("citizen_id", citizenId);
        payload.put("citizen_name", citizenName);
        payload.put("scripts_json", scriptsJson);
        payload.put("payload_sha256", payloadSha256);
        payload.put("documentType", "hycitizens:script-editor-upload");
        payload.put("schemaVersion", 1);
        payload.put("capabilities", com.electro.hycitizens.api.scripting.ScriptManager.get().getCapabilitySchema());
        payload.put("owner_uuid", playerRef.getUuid().toString());
        payload.put("resource_id", citizenId);
        payload.put("editor_type", "script");
        payload.put("base_revision", scripts.stream().mapToLong(ScriptBlock::getRevision).max().orElse(0L));
        payload.put("base_hash", sha256(scriptsJson));

        String payloadJson = gson.toJson(payload);
        String uploadUrl = baseUrl + "/api/script-editor/upload.php";

        // Store the session before the async call so it's ready when save is called
        PendingEditSession session = new PendingEditSession(
                citizenId,
                token,
                playerRef.getUuid(),
                sha256(scriptsJson),
                scriptsJson,
                System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3)
        );
        sessions.put(token, session);

        playerRef.sendMessage(Message.raw("[HyCitizens] Uploading scripts...").color(new Color(0xA0A0B0)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        String editorUrl = baseUrl + "/editor/#token=" + token;
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
        if (!playerRef.hasPermission("hycitizens.admin")) {
            playerRef.sendMessage(Message.raw("[HyCitizens] You do not have permission to apply script edits.").color(Color.RED));
            return;
        }
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
        if (!session.ownerUuid.equals(playerRef.getUuid())) {
            getLogger().atWarning().log("[HyCitizens] Player " + playerRef.getUuid()
                    + " attempted to apply script session owned by " + session.ownerUuid);
            playerRef.sendMessage(Message.raw("[HyCitizens] This script editor session belongs to another administrator.").color(Color.RED));
            return;
        }
        if (!sessions.remove(token, session)) {
            playerRef.sendMessage(Message.raw("[HyCitizens] This script editor session is already being applied.").color(Color.RED));
            return;
        }

        CitizenData citizen = HyCitizensPlugin.get().getCitizensManager().getCitizen(session.citizenId);
        if (citizen == null) {
            restoreSession(session);
            playerRef.sendMessage(Message.raw("[HyCitizens] Citizen no longer exists: " + session.citizenId).color(Color.RED));
            return;
        }

        playerRef.sendMessage(Message.raw("[HyCitizens] Fetching scripts...").color(new Color(0xA0A0B0)));

        String fetchUrl = getBaseUrl() + "/api/script-editor/fetch.php";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(fetchUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(Map.of("token", token)), StandardCharsets.UTF_8))
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 404) {
                        playerRef.sendMessage(Message.raw("[HyCitizens] Session not found on server (may have expired). Start a new one.").color(Color.RED));
                        return;
                    }
                    if (response.statusCode() != 200) {
                        restoreSession(session);
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
                                String currentJson = gson.toJson(citizen.getScripts() != null ? citizen.getScripts() : List.of());
                                if (!sha256(currentJson).equals(session.baseHash)) {
                                    restoreSession(session);
                                    playerRef.sendMessage(Message.raw("[HyCitizens] Citizen scripts changed after this editor session started. "
                                            + com.electro.hycitizens.util.JsonDiffSummary.changedTopLevelFields(
                                            "{\"scripts\":" + session.baseJson + "}",
                                            "{\"scripts\":" + currentJson + "}")
                                            + ". Start a new session before overwriting.").color(Color.RED));
                                    return;
                                }
                                citizen.setScripts(finalParsed);
                                HyCitizensPlugin.get().getCitizensManager().saveCitizen(citizen);
                                consumeRemoteSession(token);
                                int count = finalParsed.size();
                                playerRef.sendMessage(
                                    Message.raw("[HyCitizens] ").color(new Color(0x00D4FF))
                                        .insert(Message.raw("Finished fetching and applied scripts for ").color(Color.WHITE))
                                        .insert(Message.raw(citizen.getName() != null ? citizen.getName() : session.citizenId).color(new Color(0x00D4FF)))
                                        .insert(Message.raw(". (" + count + " script" + (count != 1 ? "s" : "") + " loaded)").color(Color.WHITE))
                                );
                            } catch (Exception e) {
                                restoreSession(session);
                                getLogger().atWarning().log("[HyCitizens] ScriptEditor apply error for token " + token + ": " + e.getMessage());
                                playerRef.sendMessage(Message.raw("[HyCitizens] Fetched scripts, but applying them failed. Check console.").color(Color.RED));
                            }
                        });
                    } catch (Exception e) {
                        restoreSession(session);
                        getLogger().atWarning().log("[HyCitizens] ScriptEditor parse error for token " + token + ": " + e.getMessage());
                        playerRef.sendMessage(Message.raw("[HyCitizens] Failed to parse scripts. The JSON may be malformed.").color(Color.RED));
                    }
                })
                .exceptionally(ex -> {
                    restoreSession(session);
                    getLogger().atWarning().log("[HyCitizens] ScriptEditor fetch error for token " + token + ": " + ex.getMessage());
                    playerRef.sendMessage(Message.raw("[HyCitizens] Could not reach the script editor. Please try again later.").color(Color.RED));
                    return null;
                });
    }

    // Helpers

    private String getBaseUrl() {
        String url = HyCitizensPlugin.get().getConfigManager().getString("script_editor_url", "https://hycitizens.com");
        // Strip trailing slash
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private void restoreSession(PendingEditSession session) {
        if (System.currentTimeMillis() <= session.expiryMillis) {
            sessions.putIfAbsent(session.token, session);
        }
    }

    private void consumeRemoteSession(String token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/script-editor/consume.php"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(
                        gson.toJson(Map.of("token", token)), StandardCharsets.UTF_8))
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 404) {
                        getLogger().atWarning().log("[HyCitizens] Failed to consume remote script session "
                                + token + " (HTTP " + response.statusCode() + ")");
                    }
                })
                .exceptionally(error -> {
                    getLogger().atWarning().log("[HyCitizens] Failed to consume remote script session "
                            + token + ": " + error.getMessage());
                    return null;
                });
    }

    public void revokeSessions(UUID ownerUuid) {
        sessions.entrySet().removeIf(entry -> {
            if (!entry.getValue().ownerUuid.equals(ownerUuid)) return false;
            consumeRemoteSession(entry.getKey());
            return true;
        });
    }

    // Inner types

    private static class PendingEditSession {
        final String citizenId;
        final String token;
        final UUID ownerUuid;
        final String baseHash;
        final String baseJson;
        final long expiryMillis;

        PendingEditSession(String citizenId, String token, UUID ownerUuid,
                           String baseHash, String baseJson, long expiryMillis) {
            this.citizenId = citizenId;
            this.token = token;
            this.ownerUuid = ownerUuid;
            this.baseHash = baseHash;
            this.baseJson = baseJson;
            this.expiryMillis = expiryMillis;
        }
    }
}
