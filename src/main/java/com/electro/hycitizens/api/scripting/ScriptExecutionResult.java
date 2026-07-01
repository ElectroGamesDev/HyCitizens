package com.electro.hycitizens.api.scripting;

import java.util.List;

public record ScriptExecutionResult(
        boolean success,
        boolean stopped,
        List<Failure> failures,
        List<String> trace
) {
    public static ScriptExecutionResult success(ScriptContext context) {
        return new ScriptExecutionResult(true, context.isStopped(), List.of(), context.traceSnapshot());
    }

    public static ScriptExecutionResult failure(ScriptContext context, String type, Throwable error) {
        return new ScriptExecutionResult(
                false, context.isStopped(), List.of(new Failure(type, error.getMessage(), error)), context.traceSnapshot()
        );
    }

    public record Failure(String actionType, String message, Throwable cause) {}
}
