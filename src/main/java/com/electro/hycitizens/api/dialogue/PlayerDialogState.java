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
    public Set<String> getSeenDialogs() { return seenDialogs; }
    public Set<String> getCompletedDialogs() { return completedDialogs; }
    public Map<String, Set<String>> getSeenNodes() { return seenNodes; }
    public Map<String, Set<String>> getCompletedNodes() { return completedNodes; }
    public Map<String, Integer> getDialogVisits() { return dialogVisits; }
    public Map<String, Map<String, Integer>> getNodeVisits() { return nodeVisits; }
    public Map<String, Map<String, Integer>> getResponseChoices() { return responseChoices; }
    public Map<String, Long> getFirstInteractionAt() { return firstInteractionAt; }
    public Map<String, Long> getLastInteractionAt() { return lastInteractionAt; }
    public Map<String, Long> getCompletionTimes() { return completionTimes; }
    public Map<String, String> getLastVisitedNode() { return lastVisitedNode; }
    public String getLastActiveDialogId() { return lastActiveDialogId; }
    public String getLastActiveNpcId() { return lastActiveNpcId; }
    public Map<String, Map<String, ResumableSession>> getResumableSessions() { return resumableSessions; }

    public ResumableSession getResumableSession(String dialogId, String npcId) {
        return resumableSessions.getOrDefault(dialogId, Map.of()).get(npcId != null ? npcId : "");
    }

    public void putResumableSession(ResumableSession session) {
        resumableSessions.computeIfAbsent(session.dialogId(), ignored -> new HashMap<>())
                .put(session.npcId() != null ? session.npcId() : "", session);
    }

    public void removeResumableSession(String dialogId, String npcId) {
        Map<String, ResumableSession> byNpc = resumableSessions.get(dialogId);
        if (byNpc == null) return;
        byNpc.remove(npcId != null ? npcId : "");
        if (byNpc.isEmpty()) resumableSessions.remove(dialogId);
    }
    public Map<String, Object> getCustomState() { return customState; }
    public Deque<HistoryEntry> getHistory() { return history; }

    public void recordStart(String dialogId, String npcId, long now) {
        seenDialogs.add(dialogId);
        dialogVisits.merge(dialogId, 1, Integer::sum);
        firstInteractionAt.putIfAbsent(dialogId, now);
        lastInteractionAt.put(dialogId, now);
        lastActiveDialogId = dialogId;
        lastActiveNpcId = npcId;
        addHistory(new HistoryEntry(now, "START", dialogId, null, null, npcId));
    }

    public void recordNode(String dialogId, String nodeId, String npcId, long now) {
        seenNodes.computeIfAbsent(dialogId, ignored -> new HashSet<>()).add(nodeId);
        nodeVisits.computeIfAbsent(dialogId, ignored -> new HashMap<>()).merge(nodeId, 1, Integer::sum);
        lastInteractionAt.put(dialogId, now);
        lastVisitedNode.put(dialogId, nodeId);
        lastActiveDialogId = dialogId;
        lastActiveNpcId = npcId;
        addHistory(new HistoryEntry(now, "NODE", dialogId, nodeId, null, npcId));
    }

    public void recordResponse(String dialogId, String responseId, String nodeId, String npcId, long now) {
        responseChoices.computeIfAbsent(dialogId, ignored -> new HashMap<>()).merge(responseId, 1, Integer::sum);
        completedNodes.computeIfAbsent(dialogId, ignored -> new HashSet<>()).add(nodeId);
        addHistory(new HistoryEntry(now, "RESPONSE", dialogId, nodeId, responseId, npcId));
    }

    public void recordCompletion(String dialogId, String nodeId, String responseId, String npcId, long now) {
        completedDialogs.add(dialogId);
        completionTimes.put(dialogId, now);
        completedNodes.computeIfAbsent(dialogId, ignored -> new HashSet<>()).add(nodeId);
        addHistory(new HistoryEntry(now, "COMPLETE", dialogId, nodeId, responseId, npcId));
    }

    private void addHistory(HistoryEntry entry) {
        history.addLast(entry);
        while (history.size() > 200) {
            history.removeFirst();
        }
    }

    public record ResumableSession(String dialogId, String npcId, String nodeId, long savedAt, long expiresAt) {}
    public record HistoryEntry(long timestamp, String type, String dialogId, String nodeId, String responseId, String npcId) {}
}
