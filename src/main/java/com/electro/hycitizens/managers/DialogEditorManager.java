package com.electro.hycitizens.managers;

import com.electro.hycitizens.HyCitizensPlugin;
import com.electro.hycitizens.api.dialogue.IDialogue;
import com.electro.hycitizens.api.dialogue.Dialogue;
import com.electro.hycitizens.api.scripting.ScriptManager;
import com.electro.hycitizens.util.DialogPaths;
import com.electro.hycitizens.util.ResourceId;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hypixel.hytale.component.Store;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class DialogEditorManager {

    private static DialogEditorManager instance;

    private final Gson gson = new GsonBuilder().create();
    private final HttpClient http = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    // token → session
    private final Map<String, PendingDialogEditSession> sessions = new ConcurrentHashMap<>();

    public static DialogEditorManager get() {
        if (instance == null) {
            instance = new DialogEditorManager();
        }
        return instance;
    }

    private DialogEditorManager() {}

    // Public API
    public void startEditSession(PlayerRef playerRef, String dialogId) {
        if (!playerRef.hasPermission("hycitizens.admin")) {
            playerRef.sendMessage(Message.raw("[HyCitizens] You do not have permission to edit dialogs.").color(Color.RED));
            return;
        }
        if (!ResourceId.isValid(dialogId)) {
            playerRef.sendMessage(Message.raw("[HyCitizens] Invalid dialog ID.").color(Color.RED));
            return;
        }
        IDialogue dialogue = DialogueManager.get().getDialogue(dialogId);
        if (dialogue == null) {
            playerRef.sendMessage(Message.raw("[HyCitizens] No dialog found with ID: " + dialogId).color(Color.RED));
            return;
        }

        String token = UUID.randomUUID().toString();
        String baseUrl = getBaseUrl();

        String dialogJson = DialogueManager.get().getGson().toJson(dialogue, IDialogue.class);
        String payloadSha256 = sha256(dialogJson);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("token", token);
        payload.put("dialog_id", dialogId);
        payload.put("dialog_json", dialogJson);
        payload.put("payload_sha256", payloadSha256);
        payload.put("documentType", "hycitizens:dialog-editor-upload");
        payload.put("schemaVersion", 1);
        JsonObject capabilities = ScriptManager.get().getCapabilitySchema().deepCopy();
        capabilities.add("dialogNodeTypes", gson.toJsonTree(
                com.electro.hycitizens.api.dialogue.DialogTypeRegistry.get().nodeDescriptors()));
        payload.put("capabilities", capabilities);
        payload.put("owner_uuid", playerRef.getUuid().toString());
        payload.put("resource_id", dialogId);
        payload.put("editor_type", "dialog");
        payload.put("base_revision", dialogue.getRevision());
        payload.put("base_hash", payloadSha256);
        payload.put("dialog_runtime", DialogueManager.get()
                .getRuntimeInspectorSnapshot(playerRef.getUuid(), dialogId));

        String payloadJson = gson.toJson(payload);
        String uploadUrl = baseUrl + "/api/dialog-editor/upload.php";

        // Store the session before the async call so it's ready when save is called
        PendingDialogEditSession session = new PendingDialogEditSession(
                dialogId,
                token,
                playerRef.getUuid(),
                dialogue.getRevision(),
                payloadSha256,
                dialogJson,
                System.currentTimeMillis() + TimeUnit.HOURS.toMillis(3)
        );
        sessions.put(token, session);

        playerRef.sendMessage(Message.raw("[HyCitizens] Uploading dialog...").color(new Color(0xA0A0B0)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uploadUrl))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                .build();

        http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() == 200) {
                        String editorUrl = baseUrl + "/dialog-editor/#token=" + token;
                        String saveCommand = "/hc dialog save " + token;

                        playerRef.sendMessage(
                            Message.raw("[HyCitizens] ").color(new Color(0x00D4FF))
                                .insert(Message.raw("Edit dialog for ").color(Color.WHITE))
                                .insert(Message.raw(dialogue.getTitle().isEmpty() ? dialogId : dialogue.getTitle()).color(new Color(0x00D4FF)))
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
                        String responseBody = response.body() != null ? response.body().trim() : "";
                        getLogger().atWarning().log("[HyCitizens] DialogEditor upload failed (HTTP "
                                + response.statusCode() + ") for dialog " + dialogId + " at " + uploadUrl
                                + ". Response: " + (responseBody.isEmpty() ? "<empty>" : responseBody));
                        if (response.statusCode() == 500 && responseBody.isEmpty()) {
                            playerRef.sendMessage(Message.raw("[HyCitizens] Dialog editor backend returned an empty HTTP 500. "
                                    + "Check its PHP error log and editor storage deployment settings.").color(Color.RED));
                        } else {
                            playerRef.sendMessage(Message.raw("[HyCitizens] Could not start the editor session (HTTP "
                                    + response.statusCode() + "). Check console for details.").color(Color.RED));
                        }
                    }
                })
                .exceptionally(ex -> {
                    sessions.remove(token);
                    getLogger().atWarning().log("[HyCitizens] DialogEditor upload error for dialog " + dialogId + ": " + ex.getMessage());
                    playerRef.sendMessage(Message.raw("[HyCitizens] Could not reach the dialog editor. Please try again later.").color(Color.RED));
                    return null;
                });
    }

    public void applyEditSession(PlayerRef playerRef, String token, Store<EntityStore> store) {
        if (!playerRef.hasPermission("hycitizens.admin")) {
            playerRef.sendMessage(Message.raw("[HyCitizens] You do not have permission to apply dialog edits.").color(Color.RED));
            return;
        }
        // Validate token format (UUID) to prevent path traversal on the server side
        if (!token.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
            playerRef.sendMessage(Message.raw("[HyCitizens] Invalid token format.").color(Color.RED));
            return;
        }

        PendingDialogEditSession session = sessions.get(token);
        if (session == null) {
            playerRef.sendMessage(Message.raw("[HyCitizens] Unknown token. Start a new session in Dialogs menu.").color(Color.RED));
            return;
        }
        if (System.currentTimeMillis() > session.expiryMillis) {
            sessions.remove(token);
            playerRef.sendMessage(Message.raw("[HyCitizens] Session expired. Start a new one in Dialogs menu.").color(Color.RED));
            return;
        }
        if (!session.ownerUuid.equals(playerRef.getUuid())) {
            getLogger().atWarning().log("[HyCitizens] Player " + playerRef.getUuid()
                    + " attempted to apply dialog session owned by " + session.ownerUuid);
            playerRef.sendMessage(Message.raw("[HyCitizens] This dialog editor session belongs to another administrator.").color(Color.RED));
            return;
        }
        if (!sessions.remove(token, session)) {
            playerRef.sendMessage(Message.raw("[HyCitizens] This dialog editor session is already being applied.").color(Color.RED));
            return;
        }

        playerRef.sendMessage(Message.raw("[HyCitizens] Fetching dialog...").color(new Color(0xA0A0B0)));

        String fetchUrl = getBaseUrl() + "/api/dialog-editor/fetch.php";
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
                        JsonObject root = gson.fromJson(response.body(), JsonObject.class);
                        JsonElement dialogElement = root.get("dialog");
                        String dialogId = root.get("dialog_id").getAsString();

                        if (!ResourceId.isValid(dialogId) || !session.dialogId.equals(dialogId)) {
                            restoreSession(session);
                            playerRef.sendMessage(Message.raw("[HyCitizens] Dialog identity cannot be changed inside an editor session.").color(Color.RED));
                            return;
                        }

                        // Validate JSON schema integrity
                        IDialogue dialogue = DialogueManager.get().getGson().fromJson(dialogElement, IDialogue.class);
                        if (dialogue == null) {
                            throw new IllegalArgumentException("Parsed dialogue is null");
                        }
                        if (!session.dialogId.equals(dialogue.getId())) {
                            restoreSession(session);
                            playerRef.sendMessage(Message.raw("[HyCitizens] Dialog identity cannot be changed inside an editor session.").color(Color.RED));
                            return;
                        }

                        World world = store.getExternalData().getWorld();
                        world.execute(() -> {
                            try {
                                IDialogue current = DialogueManager.get().getDialogue(dialogId);
                                if (current == null || current.getRevision() != session.baseRevision
                                        || !sha256(DialogueManager.get().getGson().toJson(current, IDialogue.class))
                                        .equals(session.baseHash)) {
                                    restoreSession(session);
                                    playerRef.sendMessage(Message.raw("[HyCitizens] Dialog changed after this editor session started. "
                                            + "Base revision " + session.baseRevision + ", current revision "
                                            + (current != null ? current.getRevision() : "deleted") + "; "
                                            + com.electro.hycitizens.util.JsonDiffSummary.changedTopLevelFields(
                                            session.baseJson,
                                            current != null ? DialogueManager.get().getGson().toJson(current, IDialogue.class) : "{}")
                                            + ". Start a new session before overwriting.").color(Color.RED));
                                    return;
                                }
                                if (dialogue instanceof Dialogue edited) edited.setRevision(session.baseRevision + 1);
                                Path dialogsDir = DialogPaths.DIALOGS_DIRECTORY;
                                Path dialogFile = ResourceId.resolveJson(dialogsDir, dialogId);
                                Files.createDirectories(dialogsDir);
                                String prettyJson = DialogueManager.get().getGson().toJson(dialogue, IDialogue.class);
                                String previousJson = Files.exists(dialogFile)
                                        ? Files.readString(dialogFile, StandardCharsets.UTF_8) : null;
                                Path temporary = Files.createTempFile(dialogsDir, "." + dialogId + "-", ".json.tmp");
                                Files.writeString(temporary, prettyJson, StandardCharsets.UTF_8);
                                try {
                                    Files.move(temporary, dialogFile,
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                                            java.nio.file.StandardCopyOption.ATOMIC_MOVE);
                                } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                                    Files.move(temporary, dialogFile,
                                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                                }

                                // Reload dialogues in-memory
                                var report = DialogueManager.get().loadDialogues();
                                if (!report.applied()) {
                                    if (previousJson != null) {
                                        Files.writeString(dialogFile, previousJson, StandardCharsets.UTF_8);
                                    } else {
                                        Files.deleteIfExists(dialogFile);
                                    }
                                    DialogueManager.get().loadDialogues();
                                    throw new IllegalArgumentException("Dialog validation failed: "
                                            + report.issues().stream().map(issue -> issue.source() + ": " + issue.message())
                                            .collect(java.util.stream.Collectors.joining("; ")));
                                }
                                consumeRemoteSession(token);

                                playerRef.sendMessage(
                                    Message.raw("[HyCitizens] ").color(new Color(0x00D4FF))
                                        .insert(Message.raw("Finished fetching and applied dialog: ").color(Color.WHITE))
                                        .insert(Message.raw(dialogue.getTitle().isEmpty() ? dialogId : dialogue.getTitle()).color(new Color(0x00D4FF)))
                                        .insert(Message.raw(" successfully.").color(Color.WHITE))
                                );
                            } catch (Exception e) {
                                restoreSession(session);
                                getLogger().atWarning().log("[HyCitizens] DialogEditor apply error for token " + token + ": " + e.getMessage());
                                playerRef.sendMessage(Message.raw("[HyCitizens] Fetched dialog, but saving to disk failed. Check console.").color(Color.RED));
                            }
                        });
                    } catch (Exception e) {
                        restoreSession(session);
                        getLogger().atWarning().log("[HyCitizens] DialogEditor parse error for token " + token + ": " + e.getMessage());
                        playerRef.sendMessage(Message.raw("[HyCitizens] Failed to parse dialog. The JSON may be malformed or invalid.").color(Color.RED));
                    }
                })
                .exceptionally(ex -> {
                    restoreSession(session);
                    getLogger().atWarning().log("[HyCitizens] DialogEditor fetch error for token " + token + ": " + ex.getMessage());
                    playerRef.sendMessage(Message.raw("[HyCitizens] Could not reach the dialog editor. Please try again later.").color(Color.RED));
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

    private void restoreSession(PendingDialogEditSession session) {
        if (System.currentTimeMillis() <= session.expiryMillis) {
            sessions.putIfAbsent(session.token, session);
        }
    }

    private void consumeRemoteSession(String token) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/dialog-editor/consume.php"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(
                        gson.toJson(Map.of("token", token)), StandardCharsets.UTF_8))
                .build();
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .thenAccept(response -> {
                    if (response.statusCode() != 200 && response.statusCode() != 404) {
                        getLogger().atWarning().log("[HyCitizens] Failed to consume remote dialog session "
                                + token + " (HTTP " + response.statusCode() + ")");
                    }
                })
                .exceptionally(error -> {
                    getLogger().atWarning().log("[HyCitizens] Failed to consume remote dialog session "
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

    private static class PendingDialogEditSession {
        final String dialogId;
        final String token;
        final UUID ownerUuid;
        final long baseRevision;
        final String baseHash;
        final String baseJson;
        final long expiryMillis;

        PendingDialogEditSession(String dialogId, String token, UUID ownerUuid,
                                 long baseRevision, String baseHash, String baseJson, long expiryMillis) {
            this.dialogId = dialogId;
            this.token = token;
            this.ownerUuid = ownerUuid;
            this.baseRevision = baseRevision;
            this.baseHash = baseHash;
            this.baseJson = baseJson;
            this.expiryMillis = expiryMillis;
        }
    }
}
