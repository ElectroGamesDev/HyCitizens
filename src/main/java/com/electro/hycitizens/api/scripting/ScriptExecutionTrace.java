package com.electro.hycitizens.api.scripting;

import java.util.List;

public record ScriptExecutionTrace(
        String scriptId,
        long timestamp,
        boolean success,
        List<ScriptExecutionResult.Failure> failures,
        List<String> entries
) {
    public ScriptExecutionTrace {
        failures = List.copyOf(failures);
        entries = List.copyOf(entries);
    }
}
