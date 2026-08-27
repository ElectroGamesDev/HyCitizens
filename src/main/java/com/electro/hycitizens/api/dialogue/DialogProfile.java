package com.electro.hycitizens.api.dialogue;

import java.util.ArrayList;
import java.util.List;

public class DialogProfile {
    private String id;
    private String defaultDialogId;
    private List<DialogSelectionRule> rules = new ArrayList<>();

    public String getId() { return id != null ? id : ""; }
    public void setId(String id) { this.id = id; }
    public String getDefaultDialogId() { return defaultDialogId != null ? defaultDialogId : ""; }
    public void setDefaultDialogId(String defaultDialogId) { this.defaultDialogId = defaultDialogId; }
    public List<DialogSelectionRule> getRules() { return rules; }
    public void setRules(List<DialogSelectionRule> rules) { this.rules = rules != null ? rules : new ArrayList<>(); }
}
