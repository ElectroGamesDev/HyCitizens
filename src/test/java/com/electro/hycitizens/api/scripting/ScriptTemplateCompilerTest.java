package com.electro.hycitizens.api.scripting;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ScriptTemplateCompilerTest {
    @Test
    void substitutesStructuredValuesWithoutJsonStringReplacement() {
        ScriptBlock template = new ScriptBlock("template", "ON_CUSTOM");
        template.setRevision(4);
        template.setTriggerParameters(Map.of("parameters_schema", Map.of(
                "message", Map.of("type", "string", "required", true),
                "amount", Map.of("type", "integer", "default", 2),
                "payload", Map.of("type", "object", "required", true)
        )));
        ScriptAction action = new ScriptAction("TEST");
        action.setParameters(Map.of(
                "message", "{{message}}",
                "amount", "{{amount}}",
                "payload", "{{payload}}"
        ));
        template.setActions(List.of(action));

        ScriptBlock instance = new ScriptBlock("instance", "ON_CUSTOM");
        instance.setTemplateParameters(Map.of(
                "message", "quote: \"safe\"",
                "payload", Map.of("enabled", true)
        ));

        ScriptBlock compiled = new ScriptTemplateCompiler(new Gson()).compile(template, instance);
        Map<String, Object> parameters = compiled.getActions().getFirst().getParameters();
        assertEquals("quote: \"safe\"", parameters.get("message"));
        assertEquals(2.0, parameters.get("amount"));
        assertTrue(parameters.get("payload") instanceof Map);
    }

    @Test
    void rejectsUnknownAndInvalidParameters() {
        ScriptBlock template = new ScriptBlock("template", "ON_CUSTOM");
        template.setTriggerParameters(Map.of("parameters_schema", Map.of(
                "count", Map.of("type", "integer", "required", true)
        )));
        ScriptBlock instance = new ScriptBlock("instance", "ON_CUSTOM");
        instance.setTemplateParameters(Map.of("count", "not-an-integer", "extra", true));

        assertThrows(IllegalArgumentException.class,
                () -> new ScriptTemplateCompiler(new Gson()).compile(template, instance));
    }
}
