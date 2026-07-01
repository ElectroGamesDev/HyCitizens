package com.electro.hycitizens.api.dialogue;

import java.util.UUID;

public record DialogPatch(
        UUID id,
        String dialogId,
        Scope scope,
        String scopeId,
        String owner,
        int priority,
        long expiresAt,
        Operation operation,
        String nodeId,
        String responseId,
        String value
) {
    public enum Scope { GLOBAL, NPC, PLAYER, SESSION }
    public enum Operation {
        REPLACE_TEXT,
        REPLACE_SPEAKER,
        REPLACE_SOUND,
        REDIRECT_NODE,
        HIDE_NODE,
        INJECT_NODE,
        ADD_RESPONSE,
        REMOVE_RESPONSE,
        REPLACE_RESPONSE_TEXT,
        ADD_CONDITION,
        REMOVE_CONDITION,
        ADD_ACTION,
        REMOVE_ACTION
    }
    public boolean isExpired(long now) { return expiresAt > 0 && now >= expiresAt; }
}
