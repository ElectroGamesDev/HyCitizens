package com.electro.hycitizens.api.dialogue;

import com.electro.hycitizens.api.scripting.ScriptContext;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DialogResolverTest {
    @Test
    void overrideWinsAndRulesUseDeterministicPriority() {
        DialogProfile profile = new DialogProfile();
        profile.setDefaultDialogId("default");
        DialogSelectionRule low = rule("low", "low-dialog", 5);
        DialogSelectionRule high = rule("high", "high-dialog", 10);
        profile.setRules(List.of(low, high));
        UUID player = UUID.randomUUID();
        ScriptContext context = new ScriptContext(null, null, null, null, "TEST", null);

        DialogResolutionResult ruleResult = new DialogResolver().resolve(profile, "npc", player, context, List.of());
        assertEquals("high-dialog", ruleResult.dialogId());
        assertEquals("high", ruleResult.matchedRuleId());

        DialogOverride override = new DialogOverride(
                UUID.randomUUID(), DialogOverride.Scope.PLAYER, player.toString(),
                "override-dialog", 1, "test", 0
        );
        DialogResolutionResult overrideResult =
                new DialogResolver().resolve(profile, "npc", player, context, List.of(override));
        assertEquals("override-dialog", overrideResult.dialogId());
        assertEquals("OVERRIDE", overrideResult.reason());
    }

    @Test
    void specificityBreaksEqualPriorityBeforeStableId() {
        DialogProfile profile = new DialogProfile();
        profile.setDefaultDialogId("default");
        DialogSelectionRule generic = rule("a-generic", "generic", 10);
        DialogSelectionRule specific = rule("z-specific", "specific", 10);
        specific.setTags(List.of("quest"));
        profile.setRules(List.of(generic, specific));
        DialogResolutionResult result = new DialogResolver().resolve(
                profile, "npc", UUID.randomUUID(),
                new ScriptContext(null, null, null, null, "TEST", null), List.of()
        );
        assertEquals("specific", result.dialogId());
    }

    private DialogSelectionRule rule(String id, String dialogId, int priority) {
        DialogSelectionRule rule = new DialogSelectionRule();
        rule.setId(id);
        rule.setDialogId(dialogId);
        rule.setPriority(priority);
        return rule;
    }
}
