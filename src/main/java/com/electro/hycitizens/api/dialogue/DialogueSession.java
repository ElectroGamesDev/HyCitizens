package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptContext;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DialogueSession {
    private final UUID sessionId;
    private final PlayerRef player;
    private final IDialogue dialogue;
    private final ScriptContext scriptContext;
    private String currentNodeId;
    private final String npcId;
    private long renderRevision;
    private final long createdAt = System.currentTimeMillis();

    public DialogueSession(@Nonnull PlayerRef player, @Nonnull IDialogue dialogue, @Nonnull ScriptContext scriptContext, @Nullable String npcId) {
        this(UUID.randomUUID(), player, dialogue, scriptContext, npcId);
    }

    public DialogueSession(@Nonnull UUID sessionId, @Nonnull PlayerRef player, @Nonnull IDialogue dialogue, @Nonnull ScriptContext scriptContext, @Nullable String npcId) {
        this.sessionId = sessionId;
        this.player = player;
        this.dialogue = dialogue;
        this.scriptContext = scriptContext;
        this.currentNodeId = dialogue.getStartNodeId();
        this.npcId = npcId;
    }

    @Nonnull
    public UUID getSessionId() { return sessionId; }

    @Nonnull
    public PlayerRef getPlayer() { return player; }

    @Nonnull
    public IDialogue getDialogue() { return dialogue; }

    @Nonnull
    public ScriptContext getScriptContext() { return scriptContext; }

    @Nonnull
    public String getCurrentNodeId() { return currentNodeId; }

    public void setCurrentNodeId(@Nonnull String currentNodeId) { this.currentNodeId = currentNodeId; }
    public long nextRenderRevision() { return ++renderRevision; }
    public long getRenderRevision() { return renderRevision; }
    public long getCreatedAt() { return createdAt; }

    @Nullable
    public IDialogueNode getCurrentNode() {
        return dialogue.getNode(currentNodeId);
    }

    @Nullable
    public String getNpcId() { return npcId; }
}
