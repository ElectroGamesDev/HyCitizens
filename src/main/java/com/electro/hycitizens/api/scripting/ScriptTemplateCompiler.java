package com.electro.hycitizens.api.scripting;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ScriptTemplateCompiler {
    private final Gson gson;
    private final Map<String, ScriptBlock> cache = new ConcurrentHashMap<>();

    public ScriptTemplateCompiler(Gson gson) {
        this.gson = gson;
    }

    public ScriptBlock compile(ScriptBlock template, ScriptBlock instance) {
        Map<String, Object> parameters = validateAndResolveParameters(template, instance.getTemplateParameters());
        String cacheKey = template.getId() + ":" + template.getRevision() + ":" + hash(gson.toJson(parameters));
        ScriptBlock compiled = cache.computeIfAbsent(cacheKey, ignored ->
                gson.fromJson(substitute(gson.toJsonTree(template), parameters), ScriptBlock.class)
        ).copy();

        compiled.getConditions().addAll(instance.getConditions());
        compiled.getActions().addAll(instance.getActions());
        compiled.setId(instance.getId());
        compiled.setName(instance.getName());
        compiled.setEnabled(instance.isEnabled());
        compiled.setPriority(instance.getPriority());
        compiled.setTriggers(instance.getTriggers());
        compiled.setTrigger(instance.getTrigger());
        compiled.setRevision(instance.getRevision());
        return compiled;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> validateAndResolveParameters(ScriptBlock template, Map<String, Object> supplied) {
        Object rawSchema = template.getTriggerParameters().get("parameters_schema");
        Map<String, Object> schema = rawSchema instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        Map<String, Object> resolved = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : schema.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> definition = entry.getValue() instanceof Map<?, ?> map
                    ? (Map<String, Object>) map : Map.of();
            Object value = supplied.containsKey(name) ? supplied.get(name) : definition.get("default");
            if (value == null && Boolean.TRUE.equals(definition.get("required"))) {
                errors.add("Missing template parameter: " + name);
            } else if (value != null && definition.get("type") != null
                    && !matchesType(value, String.valueOf(definition.get("type")))) {
                errors.add("Template parameter '" + name + "' must be " + definition.get("type"));
            } else if (value != null) {
                resolved.put(name, value);
            }
        }
        for (Map.Entry<String, Object> entry : supplied.entrySet()) {
            if (!schema.isEmpty() && !schema.containsKey(entry.getKey())) {
                errors.add("Unknown template parameter: " + entry.getKey());
            } else {
                resolved.put(entry.getKey(), entry.getValue());
            }
        }
        if (!errors.isEmpty()) throw new IllegalArgumentException(String.join("; ", errors));
        return resolved;
    }

    private JsonElement substitute(JsonElement element, Map<String, Object> parameters) {
        if (element.isJsonObject()) {
            JsonObject result = new JsonObject();
            element.getAsJsonObject().entrySet().forEach(entry ->
                    result.add(entry.getKey(), substitute(entry.getValue(), parameters)));
            return result;
        }
        if (element.isJsonArray()) {
            JsonArray result = new JsonArray();
            element.getAsJsonArray().forEach(child -> result.add(substitute(child, parameters)));
            return result;
        }
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) return element.deepCopy();

        String value = element.getAsString();
        if (value.matches("^\\{\\{[a-zA-Z0-9_.-]+}}$")) {
            String name = value.substring(2, value.length() - 2);
            return parameters.containsKey(name) ? gson.toJsonTree(parameters.get(name)) : element.deepCopy();
        }
        String replaced = value;
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            replaced = replaced.replace("{{" + entry.getKey() + "}}", String.valueOf(entry.getValue()));
        }
        return new JsonPrimitive(replaced);
    }

    private boolean matchesType(Object value, String type) {
        return switch (type.toLowerCase()) {
            case "string" -> value instanceof String;
            case "number" -> value instanceof Number;
            case "integer" -> value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            case "array" -> value instanceof Iterable<?> || value.getClass().isArray();
            case "object" -> value instanceof Map<?, ?>;
            default -> true;
        };
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
