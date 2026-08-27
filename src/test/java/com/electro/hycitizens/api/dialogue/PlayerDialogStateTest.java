package com.electro.hycitizens.api.dialogue;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PlayerDialogStateTest {
    @Test
    void recordsSummaryStateAndBoundsHistory() {
        PlayerDialogState state = new PlayerDialogState(UUID.randomUUID());
        for (int index = 0; index < 250; index++) {
            state.recordNode("intro", "node-" + index, "npc", index);
        }
        state.recordResponse("intro", "yes", "node-249", "npc", 251);
        state.recordCompletion("intro", "node-249", "yes", "npc", 252);

        assertTrue(state.getCompletedDialogs().contains("intro"));
        assertEquals(1, state.getResponseChoices().get("intro").get("yes"));
        assertEquals(200, state.getHistory().size());
    }

    @Test
    void resumableSessionsUseNestedDialogAndNpcKeys() {
        PlayerDialogState state = new PlayerDialogState(UUID.randomUUID());
        PlayerDialogState.ResumableSession session =
                new PlayerDialogState.ResumableSession("intro", "npc", "middle", 1, 100);
        state.putResumableSession(session);
        assertEquals("middle", state.getResumableSession("intro", "npc").nodeId());
        state.removeResumableSession("intro", "npc");
        assertNull(state.getResumableSession("intro", "npc"));
    }

    @Test
    void gsonDeserializationWithNullsIsDefensiveAndNullSafe() {
        com.google.gson.Gson gson = new com.google.gson.Gson();
        PlayerDialogState state = gson.fromJson("{\"playerId\":\"00000000-0000-0000-0000-000000000001\"}", PlayerDialogState.class);
        assertNotNull(state);
        assertNotNull(state.getSeenDialogs());
        assertNotNull(state.getCompletedDialogs());
        assertNotNull(state.getCustomState());
        assertNotNull(state.getDialogVisits());
        assertNotNull(state.getResumableSessions());
        assertNotNull(state.getHistory());

        // Operations should not throw NPE
        state.getCustomState().put("quest_stage", "step_2");
        assertEquals("step_2", state.getCustomState().get("quest_stage"));
        state.recordNode("dialog1", "node1", "npc1", 100L);
        state.recordResponse("dialog1", "resp1", "node1", "npc1", 101L);
        state.recordCompletion("dialog1", "node1", "resp1", "npc1", 102L);
        assertTrue(state.getCompletedDialogs().contains("dialog1"));
    }
}
