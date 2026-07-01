package com.electro.hycitizens.persistence;

import com.google.gson.JsonElement;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.function.Predicate;

public final class MigrationRegistry {
    private final Map<String, Map<Integer, UnaryOperator<JsonElement>>> migrations = new ConcurrentHashMap<>();
    private final Map<String, Map<Integer, Predicate<JsonElement>>> validators = new ConcurrentHashMap<>();

    public void register(@Nonnull String documentType, int fromVersion, @Nonnull UnaryOperator<JsonElement> migrator) {
        UnaryOperator<JsonElement> previous = migrations
                .computeIfAbsent(documentType, ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(fromVersion, migrator);
        if (previous != null) {
            throw new IllegalStateException("Duplicate migrator for " + documentType + " schema " + fromVersion);
        }
    }

    public JsonElement migrate(@Nonnull String documentType, int fromVersion, int toVersion, @Nonnull JsonElement data) {
        return migrateWithReport(documentType, fromVersion, toVersion, data, false).data();
    }

    public void registerValidator(String documentType, int version, Predicate<JsonElement> validator) {
        Predicate<JsonElement> previous = validators.computeIfAbsent(documentType, ignored -> new ConcurrentHashMap<>())
                .putIfAbsent(version, validator);
        if (previous != null) throw new IllegalStateException("Duplicate validator for " + documentType + " schema " + version);
    }

    public MigrationReport migrateWithReport(
            String documentType, int fromVersion, int toVersion, JsonElement data, boolean dryRun
    ) {
        JsonElement current = data.deepCopy();
        validate(documentType, fromVersion, current);
        java.util.List<String> steps = new java.util.ArrayList<>();
        for (int version = fromVersion; version < toVersion; version++) {
            UnaryOperator<JsonElement> migrator = migrations
                    .getOrDefault(documentType, Map.of())
                    .get(version);
            if (migrator == null) {
                throw new IllegalStateException(
                        "No migration registered for " + documentType + " from schema " + version
                );
            }
            current = migrator.apply(current);
            steps.add(version + "->" + (version + 1));
            validate(documentType, version + 1, current);
        }
        return new MigrationReport(documentType, fromVersion, toVersion, dryRun, java.util.List.copyOf(steps),
                dryRun ? data.deepCopy() : current);
    }

    private void validate(String documentType, int version, JsonElement data) {
        Predicate<JsonElement> validator = validators.getOrDefault(documentType, Map.of()).get(version);
        if (validator != null && !validator.test(data)) {
            throw new IllegalStateException("Validation failed for " + documentType + " schema " + version);
        }
    }

    public record MigrationReport(
            String documentType, int fromVersion, int toVersion, boolean dryRun,
            java.util.List<String> steps, JsonElement data
    ) {}
}
