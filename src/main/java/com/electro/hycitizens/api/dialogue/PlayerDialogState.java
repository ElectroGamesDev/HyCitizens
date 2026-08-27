package com.electro.hycitizens.api.dialogue;

import java.util.*;

public class PlayerDialogState {
    public static final int SCHEMA_VERSION = 1;
    private UUID playerId;
    private Set<String> seenDialogs = new HashSet<>();
    private Set<String> completedDialogs = new HashSet<>();
    private Map<String, Set<String>> seenNodes = new HashMap<>();
    private Map<String, Set<String>> completedNodes = new HashMap<>();
    private Map<String, Integer> dialogVisits = new HashMap<>();
    private Map<String, Map<String, Integer>> nodeVisits = new HashMap<>();
    private Map<String, Map<String, Integer>> responseChoices = new HashMap<>();
    private Map<String, Long> firstInteractionAt = new HashMap<>();
    private Map<String, Long> lastInteractionAt = new HashMap<>();
    private Map<String, Long> completionTimes = new HashMap<>();
    private Map<String, String> lastVisitedNode = new HashMap<>();
    private String lastActiveDialogId;
    private String lastActiveNpcId;
    private Map<String, Map<String, ResumableSession>> resumableSessions = new HashMap<>();
    private Map<String, Object> customState = new HashMap<>();
    private Deque<HistoryEntry> history = new ArrayDeque<>();

    public PlayerDialogState() {}
    public PlayerDialogState(UUID playerId) { this.playerId = playerId; }

    public UUID getPlayerId() { return playerId; }
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }
    public Set<String> getSeenDialogs() {
        if (seenDialogs == null) seenDialogs = new HashSet<>();
        return seenDialogs;
    }
    public Set<String> getCompletedDialogs() {
        if (completedDialogs == null) completedDialogs = new HashSet<>();
        return completedDialogs;
    }
    public Map<String, Set<String>> getSeenNodes() {
        if (seenNodes == null) seenNodes = new HashMap<>();
        return seenNodes;
    }
    public Map<String, Set<String>> getCompletedNodes() {
        if (completedNodes == null) completedNodes = new HashMap<>();
        return completedNodes;
    }
    public Map<String, Integer> getDialogVisits() {
        if (dialogVisits == null) dialogVisits = new HashMap<>();
        return dialogVisits;
    }
    public Map<String, Map<String, Integer>> getNodeVisits() {
        if (nodeVisits == null) nodeVisits = new HashMap<>();
        return nodeVisits;
    }
    public Map<String, Map<String, Integer>> getResponseChoices() {
        if (responseChoices == null) responseChoices = new HashMap<>();
        return responseChoices;
    }
    public Map<String, Long> getFirstInteractionAt() {
        if (firstInteractionAt == null) firstInteractionAt = new HashMap<>();
        return firstInteractionAt;
    }
    public Map<String, Long> getLastInteractionAt() {
        if (lastInteractionAt == null) lastInteractionAt = new HashMap<>();
        return lastInteractionAt;
    }
    public Map<String, Long> getCompletionTimes() {
        if (completionTimes == null) completionTimes = new HashMap<>();
        return completionTimes;
    }
    public Map<String, String> getLastVisitedNode() {
        if (lastVisitedNode == null) lastVisitedNode = new HashMap<>();
        return lastVisitedNode;
    }
    public String getLastActiveDialogId() { return lastActiveDialogId; }
    public String getLastActiveNpcId() { return lastActiveNpcId; }
    public Map<String, Map<String, ResumableSession>> getResumableSessions() {
        if (resumableSessions == null) resumableSessions = new HashMap<>();
        return resumableSessions;
    }

    public ResumableSession getResumableSession(String dialogId, String npcId) {
        return getResumableSessions().getOrDefault(dialogId, Map.of()).get(npcId != null ? npcId : "");
    }

    public void putResumableSession(ResumableSession session) {
        getResumableSessions().computeIfAbsent(session.dialogId(), ignored -> new HashMap<>())
                .put(session.npcId() != null ? session.npcId() : "", session);
    }

    public void removeResumableSession(String dialogId, String npcId) {
        Map<String, ResumableSession> byNpc = getResumableSessions().get(dialogId);
        if (byNpc == null) return;
        byNpc.remove(npcId != null ? npcId : "");
        if (byNpc.isEmpty()) getResumableSessions().remove(dialogId);
    }
    public Map<String, Object> getCustomState() {
        if (customState == null) customState = new HashMap<>();
        return customState;
    }
    public Deque<HistoryEntry> getHistory() {
        if (history == null) history = new ArrayDeque<>();
        return history;
    }

    public void recordStart(String dialogId, String npcId, long now) {
        getSeenDialogs().add(dialogId);
        getDialogVisits().merge(dialogId, 1, Integer::sum);
        getFirstInteractionAt().putIfAbsent(dialogId, now);
        getLastInteractionAt().put(dialogId, now);
        lastActiveDialogId = dialogId;
        lastActiveNpcId = npcId;
        addHistory(new HistoryEntry(now, "START", dialogId, null, null, npcId));
    }

    public void recordNode(String dialogId, String nodeId, String npcId, long now) {
        getSeenNodes().computeIfAbsent(dialogId, ignored -> new HashSet<>()).add(nodeId);
        getNodeVisits().computeIfAbsent(dialogId, ignored -> new HashMap<>()).merge(nodeId, 1, Integer::sum);
        getLastInteractionAt().put(dialogId, now);
        getLastVisitedNode().put(dialogId, nodeId);
        lastActiveDialogId = dialogId;
        lastActiveNpcId = npcId;
        addHistory(new HistoryEntry(now, "NODE", dialogId, nodeId, null, npcId));
    }

    public void recordResponse(String dialogId, String responseId, String nodeId, String npcId, long now) {
        getResponseChoices().computeIfAbsent(dialogId, ignored -> new HashMap<>()).merge(responseId, 1, Integer::sum);
        getCompletedNodes().computeIfAbsent(dialogId, ignored -> new HashSet<>()).add(nodeId);
        addHistory(new HistoryEntry(now, "RESPONSE", dialogId, nodeId, responseId, npcId));
    }

    public void recordCompletion(String dialogId, String nodeId, String responseId, String npcId, long now) {
        getCompletedDialogs().add(dialogId);
        getCompletionTimes().put(dialogId, now);
        getCompletedNodes().computeIfAbsent(dialogId, ignored -> new HashSet<>()).add(nodeId);
        addHistory(new HistoryEntry(now, "COMPLETE", dialogId, nodeId, responseId, npcId));
    }

    private void addHistory(HistoryEntry entry) {
        Deque<HistoryEntry> hist = getHistory();
        hist.addLast(entry);
        while (hist.size() > 200) {
            hist.removeFirst();
        }
    }

    public record ResumableSession(String dialogId, String npcId, String nodeId, long savedAt, long expiresAt) {}
    public record HistoryEntry(long timestamp, String type, String dialogId, String nodeId, String responseId, String npcId) {}
}
