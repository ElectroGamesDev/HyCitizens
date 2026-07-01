package com.electro.hycitizens.persistence;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonFileDataStoreTest {
    @TempDir
    Path temp;

    @Test
    void writesVersionedAtomicDocumentsAndIncrementsRevision() throws Exception {
        JsonFileDataStore store = new JsonFileDataStore(temp, new GsonBuilder().setPrettyPrinting().create(), new MigrationRegistry());
        TypeToken<Map<String, Object>> type = new TypeToken<>() {};
        store.write("player_state", "player-1", type, 1, new LinkedHashMap<>(Map.of("visits", 1)));
        DocumentEnvelope<Map<String, Object>> second = store.update(
                "player_state", "player-1", type, 1, Map.of(), current -> {
                    Map<String, Object> copy = new LinkedHashMap<>(current);
                    copy.put("visits", 2);
                    return copy;
                }
        );

        assertEquals(2, second.revision());
        assertEquals(2.0, store.read("player_state", "player-1", type, 1).orElseThrow().data().get("visits"));
        assertTrue(Files.readString(temp.resolve("player_state/player-1.json")).contains("\"schemaVersion\": 1"));
        assertTrue(Files.exists(temp.resolve("player_state/player-1.json.bak1")));
    }
}
