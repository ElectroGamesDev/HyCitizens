package com.electro.hycitizens.api.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DialogueDefinitionSafetyTest {
    @Test
    void nullCollectionsAreAssignedAndPublicViewsAreImmutable() {
        Dialogue dialogue = new Dialogue();
        dialogue.setNodes(null);
        DialogueNode node = new DialogueNode();
        node.setId("start");
        node.setConditions(null);
        node.setActions(null);
        node.setResponses(null);
        dialogue.putNode(node);

        assertThrows(UnsupportedOperationException.class,
                () -> dialogue.getNodes().put("other", node));
        assertThrows(UnsupportedOperationException.class,
                () -> node.getResponses().clear());
        assertSame(node, dialogue.getNode("start"));
    }

    @Test
    void unknownNodeTypesRoundTripWithoutBeingCoerced() {
        DialogTypeRegistry registry = DialogTypeRegistry.get();
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(IDialogueNode.class, new com.google.gson.JsonDeserializer<IDialogueNode>() {
                    @Override
                    public IDialogueNode deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type type,
                                                     com.google.gson.JsonDeserializationContext context) {
                        String id = json.getAsJsonObject().get("type").getAsString();
                        Class<? extends IDialogueNode> implementation = registry.nodeType(id);
                        return implementation == null
                                ? new UnresolvedDialogueNode(id, json.getAsJsonObject())
                                : context.deserialize(json, implementation);
                    }
                }).create();
        IDialogueNode node = gson.fromJson(
                "{\"type\":\"thirdparty:choice\",\"id\":\"custom\",\"payload\":{\"x\":1}}",
                IDialogueNode.class);
        assertInstanceOf(UnresolvedDialogueNode.class, node);
        assertEquals("custom", node.getId());
        assertEquals(1, ((UnresolvedDialogueNode) node).getRaw()
                .getAsJsonObject("payload").get("x").getAsInt());
    }

    @Test
    void failedNodeRegistrationDoesNotPartiallyRegisterTheNewType() {
        DialogTypeRegistry registry = DialogTypeRegistry.get();
        String id = "test:atomic_registration";
        registry.registerNodeType(id, TestNodeA.class);

        assertThrows(IllegalStateException.class,
                () -> registry.registerNodeType(id, TestNodeB.class));
        assertEquals(TestNodeA.class, registry.nodeType(id));
        assertNull(registry.nodeTypeId(TestNodeB.class));
    }

    @Test
    void mutationServiceMaterializedCacheIsBounded() {
        com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .registerTypeAdapter(IDialogue.class, new com.google.gson.JsonDeserializer<IDialogue>() {
                    @Override
                    public IDialogue deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type type,
                                                 com.google.gson.JsonDeserializationContext context) {
                        return context.deserialize(json, Dialogue.class);
                    }
                })
                .registerTypeAdapter(IDialogueNode.class, new com.google.gson.JsonDeserializer<IDialogueNode>() {
                    @Override
                    public IDialogueNode deserialize(com.google.gson.JsonElement json, java.lang.reflect.Type type,
                                                 com.google.gson.JsonDeserializationContext context) {
                        return context.deserialize(json, DialogueNode.class);
                    }
                })
                .create();
        DialogMutationService service = new DialogMutationService(gson);
        for (int i = 0; i < 300; i++) {
            UUID patchId = UUID.randomUUID();
            service.apply(new DialogPatch(patchId, "dialogue_" + i, DialogPatch.Scope.GLOBAL, null, "owner", 0, 0L, DialogPatch.Operation.REPLACE_TEXT, "node1", null, "New Text"));
        }
        for (int i = 0; i < 300; i++) {
            Dialogue d = new Dialogue();
            d.setId("dialogue_" + i);
            service.materialize(d, "npc", UUID.randomUUID(), UUID.randomUUID());
        }
        assertTrue(service.getMaterializedCacheSize() <= 256, "Materialized cache size must not exceed 256");
    }

    @Test
    void dialogueSessionTracksTransitionDepthAndResponseProcessing() {
        Dialogue dialogue = new Dialogue();
        dialogue.setId("test");
        com.electro.hycitizens.api.scripting.ScriptContext context =
                new com.electro.hycitizens.api.scripting.ScriptContext(null, null, null, null, "TEST", null);
        DialogueSession session = new DialogueSession(UUID.randomUUID(), null, dialogue, context, "npc_1");

        assertEquals(0, session.getTransitionDepth());
        assertEquals(1, session.incrementTransitionDepth());
        assertEquals(2, session.incrementTransitionDepth());
        session.resetTransitionDepth();
        assertEquals(0, session.getTransitionDepth());

        assertFalse(session.isResponseProcessing());
        session.setResponseProcessing(true);
        assertTrue(session.isResponseProcessing());
        session.setResponseProcessing(false);
        assertFalse(session.isResponseProcessing());
    }

    @Test
    void placeholderInterpolationSupportsDoubleCurlyAndDollarFormats() {
        com.electro.hycitizens.api.scripting.ScriptContext context =
                new com.electro.hycitizens.api.scripting.ScriptContext(null, null, null, null, "TEST", null);
        context.setSessionVar("gold", 500);

        assertEquals("500", com.electro.hycitizens.api.scripting.ScriptExpressionEvaluator.resolve("%session:gold%", context));
        assertEquals("500", com.electro.hycitizens.api.scripting.ScriptExpressionEvaluator.resolve("{{session.gold}}", context));
        assertEquals("500", com.electro.hycitizens.api.scripting.ScriptExpressionEvaluator.resolve("{{ session.gold }}", context));
        assertEquals("500", com.electro.hycitizens.api.scripting.ScriptExpressionEvaluator.resolve("${session.gold}", context));
        assertEquals("500", com.electro.hycitizens.api.scripting.ScriptExpressionEvaluator.resolve("${session:gold}", context));
        assertEquals("500", com.electro.hycitizens.api.scripting.ScriptExpressionEvaluator.resolve("{{session:gold}}", context));
    }

    @Test
    void responseChoiceTerminalAndContinueFallback() {
        Dialogue dialogue = new Dialogue();
        dialogue.setId("test_dialogue");
        DialogueNode startNode = new DialogueNode();
        startNode.setId("start");
        startNode.setText("Welcome!");
        
        DialogueResponse choice1 = new DialogueResponse("choice_next", "Tell me more", "next_node");
        DialogueResponse choiceExit = new DialogueResponse("choice_exit", "Goodbye", "");
        DialogueResponse choiceClose = new DialogueResponse("choice_close", "Leave", "close");
        startNode.setResponses(List.of(choice1, choiceExit, choiceClose));
        
        dialogue.setStartNode("start");
        dialogue.setNodes(Map.of("start", startNode));
        
        assertEquals("next_node", choice1.getNextNode());
        assertEquals("", choiceExit.getNextNode());
        assertEquals("close", choiceClose.getNextNode());
    }

    public static class TestNodeA extends DialogueNode {
    }

    public static class TestNodeB extends DialogueNode {
    }
}
