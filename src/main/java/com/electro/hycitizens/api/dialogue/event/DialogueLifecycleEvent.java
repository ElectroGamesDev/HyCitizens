package com.electro.hycitizens.api.dialogue.event;

import java.util.Map;
import java.util.UUID;

public record DialogueLifecycleEvent(
        Type type,
        UUID playerId,
        String npcId,
        String dialogId,
        String nodeId,
        String responseId,
        UUID sessionId,
        String reason,
        String sourceCapability,
        long timestamp,
        Map<String, Object> details
) {
    public enum Type {
        RESOLUTION_REQUESTED,
        RESOLUTION_COMPLETED,
        SESSION_PAUSED,
        SESSION_RESUMED,
        SESSION_CANCELLED,
        SESSION_EXPIRED,
        NODE_ENTERING,
        NODE_ENTERED,
        NODE_COMPLETED,
        NODE_EXITED,
        RESPONSE_AVAILABLE,
        RESPONSE_SELECTED,
        RESPONSE_REJECTED,
        STATE_CHANGED,
        MUTATION_APPLIED,
        MUTATION_REMOVED,
        VALIDATION_FAILURE,
        EXECUTION_FAILURE
    }
}
