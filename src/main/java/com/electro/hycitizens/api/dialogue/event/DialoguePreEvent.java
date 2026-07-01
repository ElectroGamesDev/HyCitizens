package com.electro.hycitizens.api.dialogue.event;

import java.util.UUID;

public final class DialoguePreEvent {
    public enum Type { RESOLUTION, NODE_ENTER, RESPONSE_SELECT }
    private final Type type;
    private final UUID playerId;
    private final String npcId;
    private final String dialogId;
    private final String nodeId;
    private final String responseId;
    private boolean cancelled;
    private String cancellationReason;

    public DialoguePreEvent(Type type, UUID playerId, String npcId, String dialogId, String nodeId, String responseId) {
        this.type = type;
        this.playerId = playerId;
        this.npcId = npcId;
        this.dialogId = dialogId;
        this.nodeId = nodeId;
        this.responseId = responseId;
    }
    public Type getType() { return type; }
    public UUID getPlayerId() { return playerId; }
    public String getNpcId() { return npcId; }
    public String getDialogId() { return dialogId; }
    public String getNodeId() { return nodeId; }
    public String getResponseId() { return responseId; }
    public boolean isCancelled() { return cancelled; }
    public String getCancellationReason() { return cancellationReason; }
    public void cancel(String reason) { cancelled = true; cancellationReason = reason; }
}
