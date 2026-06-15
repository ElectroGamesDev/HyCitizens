package com.electro.hycitizens.api.scripting;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ScriptActionHandler {

    /** The type string used in JSON, e.g. "MY_CUSTOM_ACTION" */
    String getType();

    /**
     * Execute the action.
     *
     * Return a CompletableFuture that completes when the action finishes.
     * For instant actions, return CompletableFuture.completedFuture(null).
     * For async actions (WAIT, animations), the future completes when the
     * action is done, allowing the engine to chain the next action.
     */
    CompletableFuture<Void> execute(
        ScriptContext context,
        Map<String, Object> parameters
    );
}
