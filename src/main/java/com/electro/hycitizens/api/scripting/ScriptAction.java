package com.electro.hycitizens.api.scripting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ScriptAction {
    private String type;
    private Map<String, Object> parameters = new LinkedHashMap<>();
    private String target;
    private Double targetRadius;

    // Control flow sub-actions
    private ScriptCondition condition;
    private List<ScriptAction> actions = new ArrayList<>();
    private List<ScriptAction> trueActions = new ArrayList<>();
    private List<ScriptAction> falseActions = new ArrayList<>();
    private List<Branch> branches = new ArrayList<>();

    public static class Branch {
        private int weight = 1;
        private List<ScriptAction> actions = new ArrayList<>();

        public Branch() {}

        public Branch(int weight, List<ScriptAction> actions) {
            this.weight = weight;
            this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public List<ScriptAction> getActions() {
            return actions;
        }

        public void setActions(List<ScriptAction> actions) {
            this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
        }

        public Branch copy() {
            List<ScriptAction> subCopies = new ArrayList<>();
            for (ScriptAction sa : this.actions) {
                subCopies.add(sa.copy());
            }
            return new Branch(this.weight, subCopies);
        }
    }

    public ScriptAction() {}

    public ScriptAction(String type) {
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

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public Double getTargetRadius() {
        return targetRadius;
    }

    public void setTargetRadius(Double targetRadius) {
        this.targetRadius = targetRadius;
    }

    public ScriptCondition getCondition() {
        return condition;
    }

    public void setCondition(ScriptCondition condition) {
        this.condition = condition;
    }

    public List<ScriptAction> getActions() {
        return actions;
    }

    public void setActions(List<ScriptAction> actions) {
        this.actions = actions != null ? new ArrayList<>(actions) : new ArrayList<>();
    }

    public List<ScriptAction> getTrueActions() {
        return trueActions;
    }

    public void setTrueActions(List<ScriptAction> trueActions) {
        this.trueActions = trueActions != null ? new ArrayList<>(trueActions) : new ArrayList<>();
    }

    public List<ScriptAction> getFalseActions() {
        return falseActions;
    }

    public void setFalseActions(List<ScriptAction> falseActions) {
        this.falseActions = falseActions != null ? new ArrayList<>(falseActions) : new ArrayList<>();
    }

    public List<Branch> getBranches() {
        return branches;
    }

    public void setBranches(List<Branch> branches) {
        this.branches = branches != null ? new ArrayList<>(branches) : new ArrayList<>();
    }

    public ScriptAction copy() {
        ScriptAction copy = new ScriptAction(this.type);
        copy.setParameters(this.parameters);
        copy.setTarget(this.target);
        copy.setTargetRadius(this.targetRadius);

        if (this.condition != null) {
            copy.setCondition(this.condition.copy());
        }
        if (this.actions != null) {
            List<ScriptAction> subCopies = new ArrayList<>();
            for (ScriptAction sa : this.actions) {
                subCopies.add(sa.copy());
            }
            copy.setActions(subCopies);
        }
        if (this.trueActions != null) {
            List<ScriptAction> subCopies = new ArrayList<>();
            for (ScriptAction sa : this.trueActions) {
                subCopies.add(sa.copy());
            }
            copy.setTrueActions(subCopies);
        }
        if (this.falseActions != null) {
            List<ScriptAction> subCopies = new ArrayList<>();
            for (ScriptAction sa : this.falseActions) {
                subCopies.add(sa.copy());
            }
            copy.setFalseActions(subCopies);
        }
        if (this.branches != null) {
            List<Branch> subCopies = new ArrayList<>();
            for (Branch b : this.branches) {
                subCopies.add(b.copy());
            }
            copy.setBranches(subCopies);
        }
        return copy;
    }
}
