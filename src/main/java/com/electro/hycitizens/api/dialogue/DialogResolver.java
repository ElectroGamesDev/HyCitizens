package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptCondition;
import com.electro.hycitizens.api.scripting.ScriptContext;
import com.electro.hycitizens.api.scripting.ScriptManager;

import java.util.*;

public final class DialogResolver {
    public DialogResolutionResult resolve(
            DialogProfile profile,
            String npcId,
            UUID playerId,
            ScriptContext context,
            Collection<DialogOverride> overrides
    ) {
        long now = System.currentTimeMillis();
        List<String> trace = new ArrayList<>();
        Optional<DialogOverride> override = overrides.stream()
                .filter(item -> !item.isExpired(now))
                .filter(item -> matches(item, npcId, playerId))
                .sorted(Comparator.comparingInt(DialogOverride::priority).reversed()
                        .thenComparing(Comparator.comparingInt(this::overrideSpecificity).reversed())
                        .thenComparing(item -> item.id().toString()))
                .findFirst();
        if (override.isPresent()) {
            trace.add("override:" + override.get().id());
            return new DialogResolutionResult(override.get().dialogId(), null, "OVERRIDE", List.copyOf(trace));
        }

        List<DialogSelectionRule> rules = new ArrayList<>(profile.getRules());
        rules.sort(Comparator.comparingInt(DialogSelectionRule::getPriority).reversed()
                .thenComparing(Comparator.comparingInt(this::ruleSpecificity).reversed())
                .thenComparing(DialogSelectionRule::getId));
        for (DialogSelectionRule rule : rules) {
            if (!rule.isEnabled() || (rule.getValidFrom() > 0 && now < rule.getValidFrom())
                    || (rule.getValidUntil() > 0 && now >= rule.getValidUntil())) {
                trace.add(rule.getId() + ":inactive");
                continue;
            }
            boolean eligible = true;
            for (ScriptCondition condition : rule.getConditions()) {
                if (!ScriptManager.get().evaluateCondition(condition, context)) {
                    eligible = false;
                    break;
                }
            }
            trace.add(rule.getId() + ":" + (eligible ? "matched" : "conditions-failed"));
            if (eligible) {
                return new DialogResolutionResult(rule.getDialogId(), rule.getId(), "RULE", List.copyOf(trace));
            }
        }
        trace.add("default:" + profile.getDefaultDialogId());
        return new DialogResolutionResult(profile.getDefaultDialogId(), null, "DEFAULT", List.copyOf(trace));
    }

    private boolean matches(DialogOverride item, String npcId, UUID playerId) {
        return switch (item.scope()) {
            case GLOBAL -> true;
            case NPC -> Objects.equals(item.scopeId(), npcId);
            case PLAYER -> Objects.equals(item.scopeId(), playerId.toString());
        };
    }

    private int overrideSpecificity(DialogOverride item) {
        return switch (item.scope()) {
            case PLAYER -> 3;
            case NPC -> 2;
            case GLOBAL -> 1;
        };
    }

    private int ruleSpecificity(DialogSelectionRule rule) {
        return rule.getConditions().size() * 10 + rule.getTags().size();
    }
}
