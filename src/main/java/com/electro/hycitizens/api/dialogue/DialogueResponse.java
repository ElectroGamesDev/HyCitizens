package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptAction;
import com.electro.hycitizens.api.scripting.ScriptCondition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class DialogueResponse {
    private String id;
    private String text;

    private String nextNode;
    private List<ScriptCondition> conditions = new ArrayList<>();
    private List<ScriptAction> actions = new ArrayList<>();

    public DialogueResponse() {}

    public DialogueResponse(String id, String text, String nextNode) {
        this.id = id;
        this.text = text;
        this.nextNode = nextNode;
    }

    @Nonnull
    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }

    @Nonnull
    public String getText() { return text != null ? text : ""; }
    public void setText(String text) { this.text = text; }

    @Nullable
    public String getNextNode() { return nextNode; }
    public void setNextNode(String nextNode) { this.nextNode = nextNode; }

    @Nonnull
    public List<ScriptCondition> getConditions() {
        if (conditions == null) conditions = new ArrayList<>();
        return Collections.unmodifiableList(conditions);
    }
    public void setConditions(List<ScriptCondition> conditions) {
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    @Nonnull
    public List<ScriptAction> getActions() {
        if (actions == null) actions = new ArrayList<>();
        return Collections.unmodifiableList(actions);
    }
    public void setActions(List<ScriptAction> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }
}
