package com.electro.hycitizens.api.scripting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScriptCondition {
    private String type;
    private Map<String, Object> parameters = new LinkedHashMap<>();
    private List<ScriptCondition> conditions = new ArrayList<>();
    private ScriptCondition condition; // for NOT

    public ScriptCondition() {}

    public ScriptCondition(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters != null ? new LinkedHashMap<>(parameters) : new LinkedHashMap<>();
    }

    public List<ScriptCondition> getConditions() {
        return conditions;
    }

    public void setConditions(List<ScriptCondition> conditions) {
        this.conditions = conditions != null ? new ArrayList<>(conditions) : new ArrayList<>();
    }

    public ScriptCondition getCondition() {
        return condition;
    }

    public void setCondition(ScriptCondition condition) {
        this.condition = condition;
    }

    public ScriptCondition copy() {
        ScriptCondition copy = new ScriptCondition(this.type);
        copy.setParameters(this.parameters);
        if (this.conditions != null) {
            List<ScriptCondition> subCopies = new ArrayList<>();
            for (ScriptCondition sc : this.conditions) {
                subCopies.add(sc.copy());
            }
            copy.setConditions(subCopies);
        }
        if (this.condition != null) {
            copy.setCondition(this.condition.copy());
        }
        return copy;
    }
}
