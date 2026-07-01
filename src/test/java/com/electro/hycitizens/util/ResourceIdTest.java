package com.electro.hycitizens.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ResourceIdTest {
    @Test
    void rejectsTraversalAndReservedNames() {
        assertFalse(ResourceId.isValid("../../dialog"));
        assertFalse(ResourceId.isValid("dialog/name"));
        assertFalse(ResourceId.isValid("a..b"));
        assertFalse(ResourceId.isValid("NUL"));
    }

    @Test
    void resolvesSafeDialogInsideRoot() {
        Path root = Path.of("dialogs").toAbsolutePath().normalize();
        Path target = ResourceId.resolveJson(root, "Guard_George");
        assertTrue(target.startsWith(root));
        assertEquals("Guard_George.json", target.getFileName().toString());
    }
}
