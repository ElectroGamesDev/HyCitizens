package com.electro.hycitizens.api.scripting;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public interface ScriptActionHandler {
    String getType();

    CompletableFuture<Void> execute(
        ScriptContext context,
        Map<String, Object> parameters
    );
}
