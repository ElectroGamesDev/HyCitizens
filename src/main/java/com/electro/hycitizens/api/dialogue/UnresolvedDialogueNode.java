package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptAction;
import com.electro.hycitizens.api.scripting.ScriptCondition;
import com.google.gson.JsonObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public final class UnresolvedDialogueNode implements IDialogueNode {
    private final String type;
    private final JsonObject raw;

    public UnresolvedDialogueNode(String type, JsonObject raw) {
        this.type = type;
        this.raw = raw.deepCopy();
    }

    public String getType() { return type; }
    public JsonObject getRaw() { return raw.deepCopy(); }

    @Override public boolean isCheckpoint() { return booleanValue("checkpoint"); }
    @Nonnull @Override public String getId() { return stringValue("id", ""); }
    @Nonnull @Override public String getSpeaker() { return stringValue("speaker", "NPC"); }
    @Nonnull @Override public String getText() { return stringValue("text", ""); }
    @Nullable @Override public DialogueSound getSound() { return null; }
    @Nonnull @Override public List<ScriptCondition> getConditions() { return List.of(); }
    @Nonnull @Override public List<ScriptAction> getActions() { return List.of(); }
    @Nonnull @Override public List<DialogueResponse> getResponses() { return List.of(); }
    @Nullable @Override public String getNextNodeId() { return stringValue("nextNode", null); }

    private String stringValue(String name, String fallback) {
        return raw.has(name) && raw.get(name).isJsonPrimitive() ? raw.get(name).getAsString() : fallback;
    }

    private boolean booleanValue(String name) {
        return raw.has(name) && raw.get(name).isJsonPrimitive() && raw.get(name).getAsBoolean();
    }
}
