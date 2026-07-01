package com.electro.hycitizens.api.scripting;

import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class ScriptSequencingTest {
    @Test
    void asynchronousActionsRunSequentiallyAndStopPreventsFollowingActions() {
        ScriptManager manager = ScriptManager.get();
        String firstType = "TEST_ASYNC_" + UUID.randomUUID().toString().replace("-", "");
        String secondType = "TEST_SYNC_" + UUID.randomUUID().toString().replace("-", "");
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        CompletableFuture<Void> release = new CompletableFuture<>();

        manager.registerAction(handler(firstType, (context, params) -> {
            order.add("first-start");
            return release.thenRun(() -> order.add("first-end"));
        }));
        manager.registerAction(handler(secondType, (context, params) -> {
            order.add("second");
            return CompletableFuture.completedFuture(null);
        }));

        ScriptContext context = new ScriptContext(null, null, null, null, "TEST", null);
        CompletableFuture<Void> execution = manager.executeActions(
                List.of(new ScriptAction(firstType), new ScriptAction(secondType)), context
        );
        assertEquals(List.of("first-start"), order);
        release.complete(null);
        execution.join();
        assertEquals(List.of("first-start", "first-end", "second"), order);

        order.clear();
        ScriptContext stopped = new ScriptContext(null, null, null, null, "TEST", null);
        manager.executeActions(
                List.of(new ScriptAction("STOP_SCRIPT"), new ScriptAction(secondType)), stopped
        ).join();
        assertTrue(stopped.isStopped());
        assertTrue(order.isEmpty());
    }

    private ScriptActionHandler handler(
            String type,
            java.util.function.BiFunction<ScriptContext, Map<String, Object>, CompletableFuture<Void>> action
    ) {
        return new ScriptActionHandler() {
            public String getType() { return type; }
            public CompletableFuture<Void> execute(ScriptContext context, Map<String, Object> parameters) {
                return action.apply(context, parameters);
            }
        };
    }
}
