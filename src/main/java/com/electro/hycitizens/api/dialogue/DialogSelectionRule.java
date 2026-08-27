package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptCondition;

import java.util.ArrayList;
import java.util.List;

public class DialogSelectionRule {
    private String id;
    private String dialogId;
    private int priority;
    private boolean enabled = true;
    private long validFrom;
    private long validUntil;
    private List<String> tags = new ArrayList<>();
    private List<ScriptCondition> conditions = new ArrayList<>();

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }
    public String getDialogId() { return dialogId != null ? dialogId : ""; }
    public void setDialogId(String dialogId) { this.dialogId = dialogId; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public long getValidFrom() { return validFrom; }
    public void setValidFrom(long validFrom) { this.validFrom = validFrom; }
    public long getValidUntil() { return validUntil; }
    public void setValidUntil(long validUntil) { this.validUntil = validUntil; }
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags != null ? tags : new ArrayList<>(); }
    public List<ScriptCondition> getConditions() { return conditions; }
    public void setConditions(List<ScriptCondition> conditions) { this.conditions = conditions != null ? conditions : new ArrayList<>(); }
}
