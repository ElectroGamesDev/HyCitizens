package com.electro.hycitizens.api.scripting;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ConditionTypeDescriptor(
        String id, int version, String owner, String category, String documentation,
        Map<String, Object> schema, Map<String, Object> editorHints
) {
    public String executionPolicy() { return "PURE"; }
    public String compatibility() { return owner + ":v" + version; }
    public List<String> validate(Map<String, Object> parameters) {
        Object required = schema.get("required");
        if (!(required instanceof Iterable<?> fields)) return List.of();
        List<String> errors = new ArrayList<>();
        for (Object field : fields) {
            if (!parameters.containsKey(String.valueOf(field))) errors.add("Missing parameter: " + field);
        }
        return List.copyOf(errors);
    }
}
