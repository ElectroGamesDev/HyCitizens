package com.electro.hycitizens.api.dialogue;

import java.util.UUID;

public record DialogOverride(
        UUID id,
        Scope scope,
        String scopeId,
        String dialogId,
        int priority,
        String owner,
        long expiresAt
) {
    public enum Scope { GLOBAL, NPC, PLAYER }
    public boolean isExpired(long now) { return expiresAt > 0 && now >= expiresAt; }
}
