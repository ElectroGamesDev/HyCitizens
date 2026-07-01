package com.electro.hycitizens.api.dialogue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

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

    public static class TestNodeA extends DialogueNode {
    }

    public static class TestNodeB extends DialogueNode {
    }
}
