package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptAction;
import com.electro.hycitizens.api.scripting.ScriptCondition;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class DialogueNode implements IDialogueNode {
    private String type = "hycitizens:speech";
    private String id;
    private String speaker;
    private String text;
    private DialogueSound sound;
    private List<ScriptCondition> conditions = new ArrayList<>();
    private List<ScriptAction> actions = new ArrayList<>();
    private List<DialogueResponse> responses = new ArrayList<>();
    private boolean checkpoint;

    private String nextNode;

    public DialogueNode() {}
    public String getType() { return type != null ? type : "hycitizens:speech"; }
    public void setType(String type) { this.type = type; }

    @Nonnull
    @Override
    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    @Nonnull
    @Override
    public String getSpeaker() { return speaker != null ? speaker : "NPC"; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }

    @Nonnull
    @Override
    public String getText() { return text != null ? text : ""; }
    public void setText(String text) { this.text = text; }

    @Nullable
    @Override
    public DialogueSound getSound() { return sound; }
    public void setSound(DialogueSound sound) { this.sound = sound; }

    @Nonnull
    @Override
    public List<ScriptCondition> getConditions() {
        if (conditions == null) conditions = new ArrayList<>();
        return Collections.unmodifiableList(conditions);
    }
    public void setConditions(List<ScriptCondition> conditions) {
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    @Nonnull
    @Override
    public List<ScriptAction> getActions() {
        if (actions == null) actions = new ArrayList<>();
        return Collections.unmodifiableList(actions);
    }
    public void setActions(List<ScriptAction> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    @Nonnull
    @Override
    public List<DialogueResponse> getResponses() {
        if (responses == null) responses = new ArrayList<>();
        return Collections.unmodifiableList(responses);
    }
    public void setResponses(List<DialogueResponse> responses) {
        this.responses = responses != null ? new ArrayList<>(responses) : new ArrayList<>();
    }
    public void addCondition(ScriptCondition condition) { getMutableConditions().add(condition); }
    public void addAction(ScriptAction action) { getMutableActions().add(action); }
    public void addResponse(DialogueResponse response) { getMutableResponses().add(response); }
    public void removeResponsesById(String responseId) {
        getMutableResponses().removeIf(response -> Objects.equals(response.getId(), responseId));
    }
    public void removeCondition(int index) {
        if (index >= 0 && index < getMutableConditions().size()) getMutableConditions().remove(index);
    }
    public void removeAction(int index) {
        if (index >= 0 && index < getMutableActions().size()) getMutableActions().remove(index);
    }
    private List<ScriptCondition> getMutableConditions() {
        if (conditions == null) conditions = new ArrayList<>();
        return conditions;
    }
    private List<ScriptAction> getMutableActions() {
        if (actions == null) actions = new ArrayList<>();
        return actions;
    }
    private List<DialogueResponse> getMutableResponses() {
        if (responses == null) responses = new ArrayList<>();
        return responses;
    }
    @Override public boolean isCheckpoint() { return checkpoint; }
    public void setCheckpoint(boolean checkpoint) { this.checkpoint = checkpoint; }

    @Nullable
    @Override
    public String getNextNodeId() { return nextNode; }
    public void setNextNode(String nextNode) { this.nextNode = nextNode; }

    public static Builder speech(String id, String text) {
        return new Builder(id, text);
    }

    public static class Builder {
        private final DialogueNode node;

        public Builder(String id, String text) {
            this.node = new DialogueNode();
            this.node.setId(id);
            this.node.setText(text);
            this.node.setSpeaker("NPC");
        }

        public Builder speaker(String speaker) {
            this.node.setSpeaker(speaker);
            return this;
        }

        public Builder sound(String soundId, float pitch, float volume) {
            this.node.setSound(new DialogueSound(soundId, pitch, volume));
            return this;
        }

        public Builder addCondition(ScriptCondition condition) {
            this.node.addCondition(condition);
            return this;
        }

        public Builder addAction(ScriptAction action) {
            this.node.addAction(action);
            return this;
        }

        public Builder nextNode(String nextNodeId) {
            this.node.setNextNode(nextNodeId);
            return this;
        }

        public Builder addResponse(String id, String text, String nextNodeId) {
            this.node.addResponse(new DialogueResponse(id, text, nextNodeId));
            return this;
        }

        public Builder addResponse(DialogueResponse response) {
            this.node.addResponse(response);
            return this;
        }

        public DialogueNode build() {
            return node;
        }
    }
}
