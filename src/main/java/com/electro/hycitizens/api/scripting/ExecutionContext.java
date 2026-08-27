package com.electro.hycitizens.api.scripting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExecutionContext {
    private final Map<Class<?>, Object> capabilities;
    private final AtomicBoolean cancelled;
    private final List<String> trace;

    public ExecutionContext() {
        this(new ConcurrentHashMap<>(), new AtomicBoolean(), Collections.synchronizedList(new ArrayList<>()));
    }

    protected ExecutionContext(Map<Class<?>, Object> capabilities, AtomicBoolean cancelled, List<String> trace) {
        this.capabilities = capabilities;
        this.cancelled = cancelled;
        this.trace = trace;
    }

    public <T> void putCapability(Class<T> type, T value) {
        if (value == null) capabilities.remove(type); else capabilities.put(type, value);
    }

    public <T> Optional<T> capability(Class<T> type) {
        return Optional.ofNullable(type.cast(capabilities.get(type)));
    }

    public void cancel() { cancelled.set(true); }
    public boolean isCancelled() { return cancelled.get(); }
    public void trace(String message) { trace.add(message); }
    public List<String> traceSnapshot() { return List.copyOf(trace); }

    protected Map<Class<?>, Object> sharedCapabilities() { return capabilities; }
    protected AtomicBoolean sharedCancellation() { return cancelled; }
    protected List<String> sharedTrace() { return trace; }
}
