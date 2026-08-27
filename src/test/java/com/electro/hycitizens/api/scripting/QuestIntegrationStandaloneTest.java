package com.electro.hycitizens.api.scripting;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QuestIntegrationStandaloneTest {
    @Test
    void hyCitizensLoadsWithoutHyQuests() {
        assertDoesNotThrow(() -> Class.forName(
                QuestIntegration.class.getName(),
                false,
                getClass().getClassLoader()
        ));
        assertThrows(ClassNotFoundException.class, () ->
                Class.forName("com.electro.hyquests.api.HyQuestsScriptingBridge", false, getClass().getClassLoader()));
    }
}
