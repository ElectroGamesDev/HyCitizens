package com.electro.hycitizens.managers;

import com.electro.hycitizens.api.dialogue.*;
import com.electro.hycitizens.api.dialogue.event.*;
import com.electro.hycitizens.api.scripting.*;
import com.electro.hycitizens.persistence.DataStore;
import com.electro.hycitizens.persistence.PersistenceService;
import com.electro.hycitizens.ui.DialogUI;
import com.electro.hycitizens.util.DialogPaths;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.protocol.SoundCategory;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class DialogueManager {
    private static DialogueManager instance;
    private static final TypeToken<PlayerDialogState> STATE_TYPE = TypeToken.get(PlayerDialogState.class);

    private volatile Map<String, IDialogue> dialogues = Map.of();
    private final Map<String, DialogProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, DialogOverride> overrides = new ConcurrentHashMap<>();
    private final Map<UUID, DialogueSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerDialogState> playerStates = new ConcurrentHashMap<>();
    private final Map<String, DialogContinuationProvider> continuationProviders = new ConcurrentHashMap<>();
    private final Map<String, List<DialogueListener>> ownedListeners = new ConcurrentHashMap<>();
    private final List<DialogueListener> listeners = new CopyOnWriteArrayList<>();
    private final Map<UUID, DialogResolutionResult> lastResolution = new ConcurrentHashMap<>();
    private final DialogResolver resolver = new DialogResolver();
    private final Gson gson;
    private final DataStore dataStore = PersistenceService.store();
    private final DialogMutationService mutations;

    public static DialogueManager get() {
        if (instance == null) instance = new DialogueManager();
        return instance;
    }

    private DialogueManager() {
        DialogTypeRegistry types = DialogTypeRegistry.get();
        gson = new GsonBuilder()
                .setPrettyPrinting()
                .registerTypeAdapter(IDialogue.class, new DialogInterfaceAdapter(types))
                .registerTypeAdapter(IDialogueNode.class, new NodeInterfaceAdapter(types))
                .create();
        mutations = new DialogMutationService(gson);
    }

    public Gson getGson() { return gson; }
    public DialogMutationService getMutationService() { return mutations; }
    public UUID applyPatch(DialogPatch patch) {
        UUID id = mutations.apply(patch);
        lifecycle(DialogueLifecycleEvent.Type.MUTATION_APPLIED, null, null, patch.dialogId(), patch.nodeId(),
                patch.responseId(), null, patch.owner(), Map.of("patchId", id.toString()));
        return id;
    }
    public boolean removePatch(UUID patchId) {
        boolean removed = mutations.remove(patchId);
        if (removed) lifecycle(DialogueLifecycleEvent.Type.MUTATION_REMOVED, null, null, null, null,
                null, null, "API", Map.of("patchId", patchId.toString()));
        return removed;
    }

    public void init() { loadDialogues(); }

    public void shutdown() {
        for (DialogueSession session : List.copyOf(activeSessions.values())) {
            endDialogueSession(session.getPlayer(), "SERVER_SHUTDOWN");
        }
        playerStates.clear();
    }

    public void addDialogueListener(@Nonnull DialogueListener listener) { listeners.add(listener); }
    public void addDialogueListener(@Nonnull String owner, @Nonnull DialogueListener listener) {
        listeners.add(listener);
        ownedListeners.computeIfAbsent(owner, ignored -> new CopyOnWriteArrayList<>()).add(listener);
    }
    public void removeDialogueListener(@Nonnull DialogueListener listener) { listeners.remove(listener); }
    public void removeDialogueListeners(@Nonnull String owner) {
        List<DialogueListener> owned = ownedListeners.remove(owner);
        if (owned != null) listeners.removeAll(owned);
    }

    public void registerContinuationProvider(String id, DialogContinuationProvider provider) {
        if (continuationProviders.putIfAbsent(id, provider) != null) {
            throw new IllegalStateException("Duplicate dialog continuation provider: " + id);
        }
    }

    public void registerProfile(String npcId, DialogProfile profile) { profiles.put(npcId, profile); }
    public void addOverride(DialogOverride override) {
        overrides.put(override.id(), override);
        lifecycle(DialogueLifecycleEvent.Type.MUTATION_APPLIED, null,
                override.scope() == DialogOverride.Scope.NPC ? override.scopeId() : null,
                override.dialogId(), null, null, null, override.owner(),
                Map.of("overrideId", override.id().toString(), "scope", override.scope().name()));
    }
    public boolean removeOverride(UUID id) {
        DialogOverride removed = overrides.remove(id);
        if (removed != null) {
            lifecycle(DialogueLifecycleEvent.Type.MUTATION_REMOVED, null,
                    removed.scope() == DialogOverride.Scope.NPC ? removed.scopeId() : null,
                    removed.dialogId(), null, null, null, removed.owner(),
                    Map.of("overrideId", id.toString()));
        }
        return removed != null;
    }
    public int removeOverridesByOwner(String owner) {
        List<UUID> ids = overrides.values().stream()
                .filter(override -> Objects.equals(owner, override.owner()))
                .map(DialogOverride::id).toList();
        ids.forEach(this::removeOverride);
        return ids.size();
    }
    public Optional<DialogResolutionResult> getLastResolution(UUID playerId) {
        return Optional.ofNullable(lastResolution.get(playerId));
    }

    public Map<String, Object> getRuntimeInspectorSnapshot(UUID playerId, String dialogId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("profiles", profiles.entrySet().stream()
                .filter(entry -> Objects.equals(entry.getValue().getDefaultDialogId(), dialogId)
                        || entry.getValue().getRules().stream()
                        .anyMatch(rule -> Objects.equals(rule.getDialogId(), dialogId)))
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey, Map.Entry::getValue, (left, right) -> left, LinkedHashMap::new)));
        snapshot.put("overrides", overrides.values().stream()
                .filter(override -> Objects.equals(override.dialogId(), dialogId)).toList());
        snapshot.put("mutationOverlays", mutations.snapshot().stream()
                .filter(patch -> Objects.equals(patch.dialogId(), dialogId)).toList());
        snapshot.put("playerState", getPlayerStateSnapshot(playerId));
        snapshot.put("lastResolution", getLastResolution(playerId).orElse(null));
        return Collections.unmodifiableMap(snapshot);
    }

    public DialogLoadReport loadDialogues() {
        Path directory = DialogPaths.DIALOGS_DIRECTORY;
        List<DialogLoadReport.Issue> issues = new ArrayList<>();
        try {
            Files.createDirectories(directory);
        } catch (IOException error) {
            getLogger().atWarning().log("[HyCitizens] Failed to create Dialogs directory: " + error.getMessage());
            issues.add(new DialogLoadReport.Issue(DialogLoadReport.Severity.ERROR, directory.toString(), error.getMessage()));
            return new DialogLoadReport(false, dialogues.size(), issues);
        }
        List<Path> files;
        try (var stream = Files.list(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .toList();
        } catch (IOException error) {
            issues.add(new DialogLoadReport.Issue(DialogLoadReport.Severity.ERROR, directory.toString(), error.getMessage()));
            return new DialogLoadReport(false, dialogues.size(), issues);
        }

        Map<String, IDialogue> candidate = new LinkedHashMap<>();
        for (Path file : files) {
            String fileName = file.getFileName().toString();
            try (FileReader reader = new FileReader(file.toFile())) {
                IDialogue dialogue = gson.fromJson(JsonParser.parseReader(reader), IDialogue.class);
                if (dialogue.getId().isEmpty()) {
                    throw new IllegalArgumentException("Dialogues must declare an ID");
                }
                validateDialogue(dialogue, fileName);
                if (candidate.putIfAbsent(dialogue.getId(), copyDialogue(dialogue)) != null) {
                    throw new IllegalArgumentException("Duplicate dialogue ID '" + dialogue.getId() + "'");
                }
            } catch (Exception error) {
                issues.add(new DialogLoadReport.Issue(DialogLoadReport.Severity.ERROR, fileName, error.getMessage()));
                getLogger().atWarning().log("[HyCitizens] Failed to load dialogue " + fileName + ": " + error.getMessage());
            }
        }
        if (!issues.isEmpty()) {
            getLogger().atWarning().log("[HyCitizens] Dialogue reload rejected; keeping "
                    + dialogues.size() + " previously loaded definitions.");
            return new DialogLoadReport(false, dialogues.size(), issues);
        }
        dialogues = Collections.unmodifiableMap(candidate);
        getLogger().atInfo().log("[HyCitizens] Loaded " + dialogues.size() + " dialogue presets.");
        return new DialogLoadReport(true, dialogues.size(), issues);
    }

    @Nullable
    public IDialogue getDialogue(String id) {
        IDialogue dialogue = dialogues.get(id);
        return dialogue == null ? null : copyDialogue(dialogue);
    }

    public void registerDialogue(IDialogue dialogue) {
        validateDialogue(dialogue, dialogue.getId());
        Map<String, IDialogue> updated = new LinkedHashMap<>(dialogues);
        updated.put(dialogue.getId(), copyDialogue(dialogue));
        dialogues = Collections.unmodifiableMap(updated);
    }

    public Map<String, IDialogue> getDialogues() {
        Map<String, IDialogue> snapshot = new LinkedHashMap<>();
        dialogues.forEach((id, dialogue) -> snapshot.put(id, copyDialogue(dialogue)));
        return Collections.unmodifiableMap(snapshot);
    }

    public boolean isInDialogue(PlayerRef player) { return activeSessions.containsKey(player.getUuid()); }
    @Nullable public DialogueSession getDialogueSession(PlayerRef player) { return activeSessions.get(player.getUuid()); }

    public boolean resolveAndStart(
            @Nonnull PlayerRef player,
            @Nullable String npcId,
            @Nullable String defaultDialogId,
            @Nonnull ScriptContext context
    ) {
        DialogProfile profile = profiles.get(npcId);
        if (profile == null) {
            profile = new DialogProfile();
            profile.setId(npcId != null ? npcId : "direct");
            profile.setDefaultDialogId(defaultDialogId);
        }
        DialoguePreEvent pre = firePre(DialoguePreEvent.Type.RESOLUTION, player, npcId, null, null, null);
        if (pre.isCancelled()) return false;
        lifecycle(DialogueLifecycleEvent.Type.RESOLUTION_REQUESTED, player, npcId, null, null, null, null, "INTERACTION", Map.of());
        DialogResolutionResult result = resolver.resolve(profile, npcId, player.getUuid(), context, overrides.values());
        lastResolution.put(player.getUuid(), result);
        lifecycle(DialogueLifecycleEvent.Type.RESOLUTION_COMPLETED, player, npcId, result.dialogId(), null, null, null,
                result.reason(), Map.of("trace", result.trace()));
        IDialogue selected = dialogues.get(result.dialogId());
        if (selected == null) return false;
        startDialogueSession(player, selected, context, npcId);
        return true;
    }

    public void startDialogueSession(
            @Nonnull PlayerRef player,
            @Nonnull IDialogue baseDialogue,
            @Nonnull ScriptContext context,
            @Nullable String npcId
    ) {
        DialogueSession previous = activeSessions.get(player.getUuid());
        if (previous != null) endDialogueSession(player, "REPLACED");

        UUID sessionId = UUID.randomUUID();
        IDialogue dialogue = mutations.materialize(baseDialogue, npcId, player.getUuid(), sessionId);
        DialogueSession session = new DialogueSession(sessionId, player, dialogue, context, npcId);
        PlayerDialogState state = getMutablePlayerState(player.getUuid());
        String startNode = resolveStartNode(session, state);
        if (startNode != null && dialogue.getNode(startNode) != null) session.setCurrentNodeId(startNode);
        activeSessions.put(player.getUuid(), session);

        long now = System.currentTimeMillis();
        state.recordStart(dialogue.getId(), npcId, now);
        persistState(state);
        fireStart(session);
        lifecycle(startNode != null && !startNode.equals(dialogue.getStartNodeId())
                        ? DialogueLifecycleEvent.Type.SESSION_RESUMED : DialogueLifecycleEvent.Type.STATE_CHANGED,
                player, npcId, dialogue.getId(), startNode, null, session.getSessionId(), "START", Map.of());
        displayCurrentNode(session);
    }

    private String resolveStartNode(DialogueSession session, PlayerDialogState state) {
        IDialogue dialogue = session.getDialogue();
        return switch (dialogue.getResumePolicy()) {
            case RESTART -> dialogue.getStartNodeId();
            case RESUME -> {
                PlayerDialogState.ResumableSession saved =
                        state.getResumableSession(dialogue.getId(), session.getNpcId());
                if (saved != null && saved.expiresAt() >= System.currentTimeMillis()
                        && dialogue.getNode(saved.nodeId()) != null) yield saved.nodeId();
                if (saved != null) {
                    state.removeResumableSession(dialogue.getId(), session.getNpcId());
                    lifecycle(DialogueLifecycleEvent.Type.SESSION_EXPIRED, session.getPlayer(), session.getNpcId(),
                            dialogue.getId(), saved.nodeId(), null, session.getSessionId(), "TTL", Map.of());
                }
                yield dialogue.getStartNodeId();
            }
            case CUSTOM -> {
                DialogContinuationProvider provider = continuationProviders.get(dialogue.getContinuationProviderId());
                String resolved = provider != null ? provider.resolveStartNode(session, state) : null;
                yield resolved != null && dialogue.getNode(resolved) != null ? resolved : dialogue.getStartNodeId();
            }
        };
    }

    public void endDialogueSession(@Nonnull PlayerRef player) { endDialogueSession(player, "DISMISS"); }

    public void endDialogueSession(@Nonnull PlayerRef player, @Nonnull String reason) {
        DialogueSession session = activeSessions.remove(player.getUuid());
        if (session == null) return;
        if (shouldPersist(session.getDialogue().getPersistencePolicy(), reason)) {
            PlayerDialogState state = getMutablePlayerState(player.getUuid());
            long now = System.currentTimeMillis();
            String savedNode = checkpointNode(session);
            state.putResumableSession(new PlayerDialogState.ResumableSession(
                    session.getDialogue().getId(), session.getNpcId(), savedNode, now,
                    now + session.getDialogue().getPersistencePolicy().getSessionTtlMillis()
            ));
            persistState(state);
            lifecycle(DialogueLifecycleEvent.Type.SESSION_PAUSED, player, session.getNpcId(), session.getDialogue().getId(),
                    session.getCurrentNodeId(), null, session.getSessionId(), reason, Map.of());
        } else {
            lifecycle(DialogueLifecycleEvent.Type.SESSION_CANCELLED, player, session.getNpcId(), session.getDialogue().getId(),
                    session.getCurrentNodeId(), null, session.getSessionId(), reason, Map.of());
        }
        DialogUI.get().closePlayerUI(player);
    }

    private String checkpointNode(DialogueSession session) {
        return switch (session.getDialogue().getPersistencePolicy().getCheckpointStrategy()) {
            case EVERY_NODE -> session.getCurrentNodeId();
            case NONE -> session.getDialogue().getStartNodeId();
            case EXPLICIT_ONLY -> {
                IDialogueNode current = session.getCurrentNode();
                PlayerDialogState state = getMutablePlayerState(session.getPlayer().getUuid());
                PlayerDialogState.ResumableSession previous =
                        state.getResumableSession(session.getDialogue().getId(), session.getNpcId());
                yield current != null && current.isCheckpoint() ? current.getId()
                        : (previous != null ? previous.nodeId() : session.getDialogue().getStartNodeId());
            }
        };
    }

    private boolean shouldPersist(DialogPersistencePolicy policy, String reason) {
        return switch (reason) {
            case "DISCONNECT" -> policy.isPersistOnDisconnect();
            case "SERVER_SHUTDOWN" -> policy.isPersistOnShutdown();
            case "PLUGIN_RELOAD" -> policy.isPersistOnReload();
            default -> policy.isPersistOnDismiss();
        };
    }

    public void transitionToNode(DialogueSession session, String nodeId) {
        if (!isCurrent(session)) return;
        if (session.getDialogue().getNode(nodeId) == null) {
            finishDialogue(session, session.getCurrentNodeId(), "");
            return;
        }
        lifecycle(DialogueLifecycleEvent.Type.NODE_EXITED, session.getPlayer(), session.getNpcId(), session.getDialogue().getId(),
                session.getCurrentNodeId(), null, session.getSessionId(), "TRANSITION", Map.of());
        session.setCurrentNodeId(nodeId);
        displayCurrentNode(session);
    }

    public void selectResponse(DialogueSession session, String responseId) {
        selectResponse(session, responseId, session.getRenderRevision());
    }

    public void selectResponse(DialogueSession session, String responseId, long expectedRevision) {
        if (!isCurrent(session)) {
            rejectResponse(session, responseId, "STALE_SESSION");
            return;
        }
        if (expectedRevision != session.getRenderRevision()) {
            rejectResponse(session, responseId, "STALE_REVISION");
            displayCurrentNode(session);
            return;
        }
        IDialogueNode node = session.getCurrentNode();
        if (node == null) {
            endDialogueSession(session.getPlayer(), "INVALID_NODE");
            return;
        }
        DialogueResponse selected = node.getResponses().stream()
                .filter(response -> response.getId().equals(responseId))
                .findFirst().orElse(null);
        if (selected == null) {
            if (node.getNextNodeId() != null && !node.getNextNodeId().isEmpty()) transitionToNode(session, node.getNextNodeId());
            else finishDialogue(session, node.getId(), "");
            return;
        }
        if (!conditionsPass(selected.getConditions(), session.getScriptContext())) {
            rejectResponse(session, responseId, "CONDITIONS_CHANGED");
            displayCurrentNode(session);
            return;
        }
        DialoguePreEvent responsePre = firePre(DialoguePreEvent.Type.RESPONSE_SELECT, session.getPlayer(), session.getNpcId(),
                session.getDialogue().getId(), node.getId(), selected.getId());
        if (responsePre.isCancelled()) {
            rejectResponse(session, responseId, responsePre.getCancellationReason());
            return;
        }

        lifecycle(DialogueLifecycleEvent.Type.NODE_COMPLETED, session.getPlayer(), session.getNpcId(), session.getDialogue().getId(),
                node.getId(), selected.getId(), session.getSessionId(), "RESPONSE", Map.of());
        lifecycle(DialogueLifecycleEvent.Type.RESPONSE_SELECTED, session.getPlayer(), session.getNpcId(), session.getDialogue().getId(),
                node.getId(), selected.getId(), session.getSessionId(), "PLAYER", Map.of("revision", expectedRevision));
        PlayerDialogState state = getMutablePlayerState(session.getPlayer().getUuid());
        state.recordResponse(session.getDialogue().getId(), selected.getId(), node.getId(), session.getNpcId(), System.currentTimeMillis());
        persistState(state);
        DialogueResponse finalSelected = selected;
        executeWithPolicy(session, selected.getActions(), node.getNextNodeId(), () -> {
            String next = finalSelected.getNextNode();
            if (next == null || next.isEmpty()) next = node.getNextNodeId();
            if (next != null && !next.isEmpty()) transitionToNode(session, next);
            else finishDialogue(session, node.getId(), finalSelected.getId());
        });
    }

    private void rejectResponse(DialogueSession session, String responseId, String reason) {
        lifecycle(DialogueLifecycleEvent.Type.RESPONSE_REJECTED, session.getPlayer(), session.getNpcId(), session.getDialogue().getId(),
                session.getCurrentNodeId(), responseId, session.getSessionId(), reason, Map.of());
    }

    private void displayCurrentNode(DialogueSession session) {
        if (!isCurrent(session)) return;
        IDialogueNode node = session.getCurrentNode();
        if (node == null) {
            endDialogueSession(session.getPlayer(), "INVALID_NODE");
            return;
        }
        lifecycle(DialogueLifecycleEvent.Type.NODE_ENTERING, session.getPlayer(), session.getNpcId(), session.getDialogue().getId(),
                node.getId(), null, session.getSessionId(), "TRANSITION", Map.of());
        DialoguePreEvent nodePre = firePre(DialoguePreEvent.Type.NODE_ENTER, session.getPlayer(), session.getNpcId(),
                session.getDialogue().getId(), node.getId(), null);
        if (nodePre.isCancelled()) {
            endDialogueSession(session.getPlayer(), nodePre.getCancellationReason() != null
                    ? nodePre.getCancellationReason() : "NODE_ENTER_CANCELLED");
            return;
        }
        if (!conditionsPass(node.getConditions(), session.getScriptContext())) {
            lifecycle(DialogueLifecycleEvent.Type.VALIDATION_FAILURE, session.getPlayer(), session.getNpcId(), session.getDialogue().getId(),
                    node.getId(), null, session.getSessionId(), "NODE_CONDITIONS_FAILED", Map.of());
            if (node.getNextNodeId() != null && !node.getNextNodeId().isEmpty()) transitionToNode(session, node.getNextNodeId());
            else endDialogueSession(session.getPlayer(), "NODE_CONDITIONS_FAILED");
            return;
        }

        PlayerDialogState state = getMutablePlayerState(session.getPlayer().getUuid());
        state.recordNode(session.getDialogue().getId(), node.getId(), session.getNpcId(), System.currentTimeMillis());
        if (node.isCheckpoint()) {
            long now = System.currentTimeMillis();
            state.putResumableSession(new PlayerDialogState.ResumableSession(
                    session.getDialogue().getId(), session.getNpcId(), node.getId(), now,
                    now + session.getDialogue().getPersistencePolicy().getSessionTtlMillis()
            ));
        }
        persistState(state);
        executeWithPolicy(session, node.getActions(), node.getNextNodeId(), () -> renderNode(session, node));
    }

    private void renderNode(DialogueSession session, IDialogueNode node) {
        if (!isCurrent(session)) return;
        DialogueSound sound = node.getSound();
        if (sound != null && sound.getId() != null && !sound.getId().isEmpty()) {
            try {
                SoundUtil.playSoundEvent2dToPlayer(session.getPlayer(), Integer.parseInt(sound.getId()),
                        SoundCategory.SFX, sound.getVolume(), sound.getPitch());
            } catch (NumberFormatException ignored) {}
        }
        List<DialogueResponse> eligible = node.getResponses().stream()
                .filter(response -> conditionsPass(response.getConditions(), session.getScriptContext()))
                .toList();
        long revision = session.nextRenderRevision();
        for (DialogueResponse response : eligible) {
            lifecycle(DialogueLifecycleEvent.Type.RESPONSE_AVAILABLE, session.getPlayer(), session.getNpcId(), session.getDialogue().getId(),
                    node.getId(), response.getId(), session.getSessionId(), "RENDER", Map.of("revision", revision));
        }
        lifecycle(DialogueLifecycleEvent.Type.NODE_ENTERED, session.getPlayer(), session.getNpcId(), session.getDialogue().getId(),
                node.getId(), null, session.getSessionId(), "RENDER", Map.of("revision", revision));
        DialogUI.get().openDialogueUI(session.getPlayer(), session, node, eligible);
    }

    private void executeWithPolicy(DialogueSession session, List<ScriptAction> actions, String fallbackNode, Runnable onSuccess) {
        ScriptManager.get().executeActionsResult(actions, session.getScriptContext()).thenAccept(result -> {
            Runnable continuation = () -> {
                if (!isCurrent(session)) return;
                if (result.success() || session.getDialogue().getActionFailurePolicy() == DialogActionFailurePolicy.CONTINUE) {
                    onSuccess.run();
                    return;
                }
                lifecycle(DialogueLifecycleEvent.Type.EXECUTION_FAILURE, session.getPlayer(), session.getNpcId(),
                        session.getDialogue().getId(), session.getCurrentNodeId(), null, session.getSessionId(),
                        session.getDialogue().getActionFailurePolicy().name(), Map.of(
                                "failures", result.failures().stream().map(ScriptExecutionResult.Failure::message).toList()
                        ));
                switch (session.getDialogue().getActionFailurePolicy()) {
                    case STOP -> endDialogueSession(session.getPlayer(), "ACTION_FAILURE");
                    case FALLBACK_EDGE -> {
                        if (fallbackNode != null && !fallbackNode.isEmpty()) transitionToNode(session, fallbackNode);
                        else endDialogueSession(session.getPlayer(), "ACTION_FAILURE");
                    }
                    case RETRY -> ScriptManager.get().executeActionsResult(actions, session.getScriptContext())
                            .thenAccept(retry -> runOnWorld(session, retry.success() ? onSuccess
                                    : () -> endDialogueSession(session.getPlayer(), "ACTION_RETRY_FAILED")));
                    case CONTINUE -> onSuccess.run();
                }
            };
            runOnWorld(session, continuation);
        });
    }

    private boolean conditionsPass(List<ScriptCondition> conditions, ScriptContext context) {
        for (ScriptCondition condition : conditions) {
            if (!ScriptManager.get().evaluateCondition(condition, context)) return false;
        }
        return true;
    }

    private void finishDialogue(DialogueSession session, String lastNodeId, String lastResponseId) {
        if (!activeSessions.remove(session.getPlayer().getUuid(), session)) return;
        DialogUI.get().closePlayerUI(session.getPlayer());
        PlayerDialogState state = getMutablePlayerState(session.getPlayer().getUuid());
        state.recordCompletion(session.getDialogue().getId(), lastNodeId, lastResponseId, session.getNpcId(), System.currentTimeMillis());
        if (session.getDialogue().getPersistencePolicy().isClearOnCompletion()) {
            state.removeResumableSession(session.getDialogue().getId(), session.getNpcId());
        }
        persistState(state);
        fireComplete(session, lastNodeId, lastResponseId);

        String nextId = session.getDialogue().getNextDialogueIdOnComplete();
        IDialogue next = nextId != null ? dialogues.get(nextId) : null;
        if (next != null) {
            startDialogueSession(session.getPlayer(), next, session.getScriptContext(), session.getNpcId());
        }
    }

    private PlayerDialogState getMutablePlayerState(UUID playerId) {
        return playerStates.computeIfAbsent(playerId, id -> {
            try {
                PlayerDialogState state = dataStore.read(
                        "dialog_state", id.toString(), STATE_TYPE, PlayerDialogState.SCHEMA_VERSION
                ).map(com.electro.hycitizens.persistence.DocumentEnvelope::data).orElse(new PlayerDialogState(id));
                state.setPlayerId(id);
                return state;
            } catch (IOException error) {
                getLogger().atWarning().log("[HyCitizens] Failed to load dialog state for " + id + ": " + error.getMessage());
                return new PlayerDialogState(id);
            }
        });
    }

    public PlayerDialogState getPlayerStateSnapshot(UUID playerId) {
        return gson.fromJson(gson.toJson(getMutablePlayerState(playerId)), PlayerDialogState.class);
    }

    public void setCustomPlayerState(UUID playerId, String namespacedKey, Object value) {
        if (namespacedKey == null || !namespacedKey.matches("[a-z0-9_.-]+:[a-z0-9_.-]+")) {
            throw new IllegalArgumentException("Custom dialog-state keys must be namespaced");
        }
        PlayerDialogState state = getMutablePlayerState(playerId);
        if (value == null) state.getCustomState().remove(namespacedKey);
        else state.getCustomState().put(namespacedKey, value);
        persistState(state);
    }

    public void unloadPlayerState(UUID playerId) {
        PlayerDialogState state = playerStates.remove(playerId);
        if (state != null) persistState(state);
        dataStore.unload("dialog_state", playerId.toString());
    }

    private void persistState(PlayerDialogState state) {
        try {
            dataStore.write("dialog_state", state.getPlayerId().toString(), STATE_TYPE,
                    PlayerDialogState.SCHEMA_VERSION, state);
            lifecycle(DialogueLifecycleEvent.Type.STATE_CHANGED, null, null, null, null, null, null,
                    "PERSISTED", Map.of("playerId", state.getPlayerId().toString()));
        } catch (IOException error) {
            getLogger().atWarning().log("[HyCitizens] Failed to persist dialog state for "
                    + state.getPlayerId() + ": " + error.getMessage());
        }
    }

    private boolean isCurrent(DialogueSession session) {
        return activeSessions.get(session.getPlayer().getUuid()) == session;
    }

    private void runOnWorld(DialogueSession session, Runnable action) {
        World world = Universe.get().getWorld(session.getPlayer().getWorldUuid());
        if (world != null) world.execute(action); else action.run();
    }

    private IDialogue copyDialogue(IDialogue dialogue) {
        return gson.fromJson(gson.toJson(dialogue, IDialogue.class), IDialogue.class);
    }

    private void fireStart(DialogueSession session) {
        DialogueStartEvent event = new DialogueStartEvent(DialogueSessionSnapshot.from(session));
        for (DialogueListener listener : listeners) {
            try { listener.onDialogueStart(event); }
            catch (Exception error) { logListenerFailure("start", error); }
        }
    }

    private void fireComplete(DialogueSession session, String nodeId, String responseId) {
        DialogueCompleteEvent event = new DialogueCompleteEvent(
                DialogueSessionSnapshot.from(session), nodeId, responseId);
        for (DialogueListener listener : listeners) {
            try { listener.onDialogueComplete(event); }
            catch (Exception error) { logListenerFailure("complete", error); }
        }
    }

    private DialoguePreEvent firePre(
            DialoguePreEvent.Type type, PlayerRef player, String npcId, String dialogId, String nodeId, String responseId
    ) {
        DialoguePreEvent event = new DialoguePreEvent(type, player.getUuid(), npcId, dialogId, nodeId, responseId);
        for (DialogueListener listener : listeners) {
            try { listener.onDialoguePre(event); }
            catch (Exception error) { logListenerFailure("pre-" + type, error); }
        }
        return event;
    }

    private void lifecycle(
            DialogueLifecycleEvent.Type type, PlayerRef player, String npcId, String dialogId, String nodeId,
            String responseId, UUID sessionId, String reason, Map<String, Object> details
    ) {
        DialogueLifecycleEvent event = new DialogueLifecycleEvent(
                type, player != null ? player.getUuid() : null, npcId, dialogId, nodeId, responseId,
                sessionId, reason, "hycitizens:dialog", System.currentTimeMillis(), Map.copyOf(details)
        );
        for (DialogueListener listener : listeners) {
            try { listener.onDialogueLifecycle(event); }
            catch (Exception error) { logListenerFailure(type.name(), error); }
        }
    }

    private void logListenerFailure(String phase, Exception error) {
        getLogger().atWarning().log("[HyCitizens] Dialogue listener failure during " + phase + ": " + error.getMessage());
    }

    private void validateDialogue(IDialogue dialogue, String source) {
        if (dialogue == null || dialogue.getId().isEmpty()) throw new IllegalArgumentException("Dialogue ID is required");
        if (dialogue.getStartNodeId().isEmpty() || dialogue.getNode(dialogue.getStartNodeId()) == null) {
            throw new IllegalArgumentException("Dialogue " + dialogue.getId() + " has an invalid start node in " + source);
        }
        for (Map.Entry<String, IDialogueNode> entry : dialogue.getNodes().entrySet()) {
            IDialogueNode node = entry.getValue();
            if (node == null || !entry.getKey().equals(node.getId())) {
                throw new IllegalArgumentException("Dialogue " + dialogue.getId() + " has an invalid node identity");
            }
            if (node.getNextNodeId() != null && !node.getNextNodeId().isEmpty()
                    && dialogue.getNode(node.getNextNodeId()) == null) {
                throw new IllegalArgumentException("Node " + node.getId() + " points to missing node " + node.getNextNodeId());
            }
            Set<String> responseIds = new HashSet<>();
            for (DialogueResponse response : node.getResponses()) {
                if (!responseIds.add(response.getId())) {
                    throw new IllegalArgumentException("Duplicate response ID " + response.getId() + " in node " + node.getId());
                }
                if (response.getNextNode() != null && !response.getNextNode().isEmpty()
                        && dialogue.getNode(response.getNextNode()) == null) {
                    throw new IllegalArgumentException("Response " + response.getId() + " points to a missing node");
                }
            }
        }
    }

    private static class DialogInterfaceAdapter implements JsonDeserializer<IDialogue>, JsonSerializer<IDialogue> {
        private final DialogTypeRegistry registry;
        DialogInterfaceAdapter(DialogTypeRegistry registry) { this.registry = registry; }
        public IDialogue deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            JsonObject object = json.getAsJsonObject();
            String typeId = object.has("type") ? object.get("type").getAsString() : "hycitizens:dialog";
            Class<? extends IDialogue> implementation = registry.dialogType(typeId);
            if (implementation == null) throw new JsonParseException("Unknown dialogue type: " + typeId);
            return context.deserialize(json, implementation);
        }
        public JsonElement serialize(IDialogue source, Type type, JsonSerializationContext context) {
            JsonObject object = context.serialize(source, source.getClass()).getAsJsonObject();
            String typeId = registry.dialogTypeId(source.getClass());
            if (typeId != null) object.addProperty("type", typeId);
            return object;
        }
    }

    private static class NodeInterfaceAdapter implements JsonDeserializer<IDialogueNode>, JsonSerializer<IDialogueNode> {
        private final DialogTypeRegistry registry;
        NodeInterfaceAdapter(DialogTypeRegistry registry) { this.registry = registry; }
        public IDialogueNode deserialize(JsonElement json, Type type, JsonDeserializationContext context) {
            JsonObject object = json.getAsJsonObject();
            String typeId = object.has("type") ? object.get("type").getAsString() : "hycitizens:speech";
            Class<? extends IDialogueNode> implementation = registry.nodeType(typeId);
            return implementation != null
                    ? context.deserialize(json, implementation)
                    : new UnresolvedDialogueNode(typeId, object);
        }
        public JsonElement serialize(IDialogueNode source, Type type, JsonSerializationContext context) {
            if (source instanceof UnresolvedDialogueNode unresolved) return unresolved.getRaw();
            JsonObject object = context.serialize(source, source.getClass()).getAsJsonObject();
            String typeId = registry.nodeTypeId(source.getClass());
            if (typeId != null) object.addProperty("type", typeId);
            return object;
        }
    }
}
