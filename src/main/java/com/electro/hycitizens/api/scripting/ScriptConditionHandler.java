package com.electro.hycitizens.api.scripting;

import java.util.Map;

public interface ScriptConditionHandler {
    String getType();

    boolean evaluate(
        ScriptContext context,
        Map<String, Object> parameters
    );
}
