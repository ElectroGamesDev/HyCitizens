package com.electro.hycitizens.persistence;

import com.google.gson.GsonBuilder;

import java.nio.file.Paths;

public final class PersistenceService {
    private static final MigrationRegistry MIGRATIONS = new MigrationRegistry();
    private static final JsonFileDataStore STORE = new JsonFileDataStore(
            Paths.get("mods", "HyCitizensData", "DataStore"),
            new GsonBuilder().setPrettyPrinting().create(),
            MIGRATIONS
    );

    private PersistenceService() {
    }

    public static DataStore store() {
        return STORE;
    }

    public static MigrationRegistry migrations() {
        return MIGRATIONS;
    }
}
