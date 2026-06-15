package com.electro.hycitizens.api.scripting;

import java.util.Map;

public interface ScriptConditionHandler {

    /** The type string used in JSON, e.g. "MY_CUSTOM_CONDITION" */
    String getType();

    /**
     * Evaluate the condition.
     * @return true if the condition passes, false otherwise
     */
    boolean evaluate(
        ScriptContext context,
        Map<String, Object> parameters
    );
}
