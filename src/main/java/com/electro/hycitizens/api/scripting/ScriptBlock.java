package com.electro.hycitizens.api.scripting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScriptBlock {
    private String id;
    private String name = "";
    private boolean enabled = true;
    private int priority = 0;
    private String trigger;
    private String[] triggers;
    private Map<String, Object> triggerParameters = new LinkedHashMap<>();
    private List<ScriptCondition> conditions = new ArrayList<>();
    private List<ScriptAction> actions = new ArrayList<>();

    // Template inheritance fields
    private String templateId;
    private Map<String, Object> templateParameters = new LinkedHashMap<>();

    public ScriptBlock() {}

    public ScriptBlock(String id, String trigger) {
        this.id = id;
        this.trigger = trigger;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name != null ? name : "";
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }

    public String[] getTriggers() {
        return triggers;
    }

    public void setTriggers(String[] triggers) {
        this.triggers = triggers;
    }

    public boolean matchesTrigger(String triggerType) {
        if (triggerType == null) return false;
        if (triggerType.equalsIgnoreCase(this.trigger)) {
            return true;
        }
        if (this.triggers != null) {
            for (String t : this.triggers) {
                if (triggerType.equalsIgnoreCase(t)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Map<String, Object> getTriggerParameters() {
        return triggerParameters;
    }

    public void setTriggerParameters(Map<String, Object> triggerParameters) {
        this.triggerParameters = triggerParameters != null ? new LinkedHashMap<>(triggerParameters) : new LinkedHashMap<>();
    }

    public List<ScriptCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<ScriptCondition> conditions) {
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    public List<ScriptAction> getActions() {
        return actions;
    }

    public void setActions(List<ScriptAction> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public Map<String, Object> getTemplateParameters() {
        return templateParameters;
    }

    public void setTemplateParameters(Map<String, Object> templateParameters) {
        this.templateParameters = templateParameters != null ? new LinkedHashMap<>(templateParameters) : new LinkedHashMap<>();
    }

    public ScriptBlock copy() {
        ScriptBlock copy = new ScriptBlock(this.id, this.trigger);
        copy.setName(this.name);
        copy.setEnabled(this.enabled);
        copy.setPriority(this.priority);
        copy.setTriggerParameters(this.triggerParameters);
        if (this.triggers != null) {
            copy.setTriggers(this.triggers.clone());
        }
        copy.setTemplateId(this.templateId);
        copy.setTemplateParameters(this.templateParameters);

        if (this.conditions != null) {
            List<ScriptCondition> subCopies = new ArrayList<>();
            for (ScriptCondition sc : this.conditions) {
                subCopies.add(sc.copy());
            }
            copy.setConditions(subCopies);
        }
        if (this.actions != null) {
            List<ScriptAction> subCopies = new ArrayList<>();
            for (ScriptAction sa : this.actions) {
                subCopies.add(sa.copy());
            }
            copy.setActions(subCopies);
        }
        return copy;
    }
}
