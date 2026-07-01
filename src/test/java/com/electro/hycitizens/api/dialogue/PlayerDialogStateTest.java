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
}
