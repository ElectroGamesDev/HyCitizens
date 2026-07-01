package com.electro.hycitizens.api.scripting;

import java.util.Map;

public record ActionTypeDescriptor(
        String id, int version, String owner, String category, String documentation,
        Map<String, Object> schema, Map<String, Object> editorHints, boolean privileged
) {
    public String executionPolicy() { return privileged ? "PRIVILEGED" : "STANDARD"; }
    public String compatibility() { return owner + ":v" + version; }
    public java.util.List<String> validate(Map<String, Object> parameters) {
        Object required = schema.get("required");
        if (!(required instanceof Iterable<?> fields)) return java.util.List.of();
        java.util.List<String> errors = new java.util.ArrayList<>();
        for (Object field : fields) {
            if (!parameters.containsKey(String.valueOf(field))) errors.add("Missing parameter: " + field);
        }
        return java.util.List.copyOf(errors);
    }
}
