package com.electro.hycitizens.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class JsonDiffSummary {
    private JsonDiffSummary() {}

    public static String changedTopLevelFields(String beforeJson, String afterJson) {
        try {
            JsonObject before = JsonParser.parseString(beforeJson).getAsJsonObject();
            JsonObject after = JsonParser.parseString(afterJson).getAsJsonObject();
            Set<String> keys = new LinkedHashSet<>(before.keySet());
            keys.addAll(after.keySet());
            keys.removeIf(key -> Objects.equals(before.get(key), after.get(key)));
            return keys.isEmpty() ? "content hash changed" : "changed fields: " + String.join(", ", keys);
        } catch (RuntimeException error) {
            return "content hash changed";
        }
    }
}
