package com.electro.hycitizens.api.dialogue;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class DialogTypeRegistry {
    private static final DialogTypeRegistry INSTANCE = new DialogTypeRegistry();

    private final Map<String, Class<? extends IDialogue>> dialogTypes = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> dialogIds = new ConcurrentHashMap<>();
    private final Map<String, Class<? extends IDialogueNode>> nodeTypes = new ConcurrentHashMap<>();
    private final Map<Class<?>, String> nodeIds = new ConcurrentHashMap<>();
    private final Map<String, TypeDescriptor> nodeDescriptors = new ConcurrentHashMap<>();

    private DialogTypeRegistry() {
        registerDialogType("hycitizens:dialog", Dialogue.class);
        registerNodeType("hycitizens:speech", DialogueNode.class);
    }

    public static DialogTypeRegistry get() {
        return INSTANCE;
    }

    public void registerDialogType(String id, Class<? extends IDialogue> type) {
        register(id, type, dialogTypes, dialogIds, "dialog");
    }

    public void registerNodeType(String id, Class<? extends IDialogueNode> type) {
        registerNodeType(id, type, Map.of("type", "object"), Map.of());
    }

    public synchronized void registerNodeType(String id, Class<? extends IDialogueNode> type,
                                              Map<String, Object> schema, Map<String, Object> editorHints) {
        String normalized = normalize(id);
        TypeDescriptor descriptor = new TypeDescriptor(normalized, schema, editorHints);
        register(id, type, nodeTypes, nodeIds, "dialog node");
        nodeDescriptors.put(normalized, descriptor);
    }

    public Class<? extends IDialogue> dialogType(String id) {
        return dialogTypes.get(normalize(id));
    }

    public Class<? extends IDialogueNode> nodeType(String id) {
        return nodeTypes.get(normalize(id));
    }

    public String dialogTypeId(Class<?> type) {
        return dialogIds.get(type);
    }

    public String nodeTypeId(Class<?> type) {
        return nodeIds.get(type);
    }

    public List<TypeDescriptor> nodeDescriptors() {
        return nodeDescriptors.values().stream()
                .sorted(Comparator.comparing(TypeDescriptor::id)).toList();
    }

    public record TypeDescriptor(String id, Map<String, Object> schema, Map<String, Object> editorHints) {
        public TypeDescriptor {
            schema = Map.copyOf(schema);
            editorHints = Map.copyOf(editorHints);
        }
    }

    private static synchronized <T> void register(
            String id,
            Class<? extends T> type,
            Map<String, Class<? extends T>> byId,
            Map<Class<?>, String> byType,
            String kind
    ) {
        String normalized = normalize(id);
        if (normalized.isEmpty() || !normalized.contains(":")) {
            throw new IllegalArgumentException("Namespaced " + kind + " type ID is required");
        }
        Class<? extends T> previousType = byId.get(normalized);
        String previousId = byType.get(type);
        if ((previousType != null && previousType != type)
                || (previousId != null && !previousId.equals(normalized))) {
            throw new IllegalStateException("Duplicate " + kind + " type registration: " + normalized);
        }
        byId.putIfAbsent(normalized, type);
        byType.putIfAbsent(type, normalized);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
