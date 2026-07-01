package com.electro.hycitizens.api.scripting;

import java.util.Map;

public record TriggerTypeDescriptor(String id, int version, String owner, Map<String, Object> schema, String documentation) {
    public String compatibility() { return owner + ":v" + version; }
}
