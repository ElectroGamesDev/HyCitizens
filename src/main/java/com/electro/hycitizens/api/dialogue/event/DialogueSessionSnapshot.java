package com.electro.hycitizens.api.dialogue.event;

import com.electro.hycitizens.api.dialogue.DialogueSession;

import java.util.UUID;

public record DialogueSessionSnapshot(
        UUID sessionId,
        UUID playerId,
        String npcId,
        String dialogId,
        long dialogRevision,
        String currentNodeId,
        long renderRevision,
        long createdAt
) {
    public static DialogueSessionSnapshot from(DialogueSession session) {
        return new DialogueSessionSnapshot(
                session.getSessionId(),
                session.getPlayer().getUuid(),
                session.getNpcId(),
                session.getDialogue().getId(),
                session.getDialogue().getRevision(),
                session.getCurrentNodeId(),
                session.getRenderRevision(),
                session.getCreatedAt()
        );
    }
}
