package com.electro.hycitizens.api.scripting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionContextTest {
    @Test
    void targetedContextsShareCancellationAndTrace() {
        ScriptContext parent = new ScriptContext(null, null, null, null, "TEST", null);
        ScriptContext child = new ScriptContext(parent, null);
        child.trace("child");
        child.setStopped(true);
        assertTrue(parent.isStopped());
        assertEquals("child", parent.traceSnapshot().getFirst());
    }
}
