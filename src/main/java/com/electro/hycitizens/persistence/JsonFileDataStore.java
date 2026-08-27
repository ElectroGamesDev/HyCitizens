package com.electro.hycitizens.persistence;

import com.electro.hycitizens.util.ResourceId;
import com.google.gson.*;
import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class JsonFileDataStore implements DataStore {
    private static final int MAX_BACKUPS = 3;

    private final Path root;
    private final Gson gson;
    private final MigrationRegistry migrations;
    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    public JsonFileDataStore(@Nonnull Path root, @Nonnull Gson gson, @Nonnull MigrationRegistry migrations) {
        this.root = root.toAbsolutePath().normalize();
        this.gson = gson;
        this.migrations = migrations;
    }

    @Override
    public <T> Optional<DocumentEnvelope<T>> read(
            String namespace,
            String id,
            TypeToken<T> type,
            int currentSchemaVersion
    ) throws IOException {
        Path file = resolve(namespace, id);
        synchronized (lock(namespace, id)) {
            if (!Files.exists(file)) {
                return Optional.empty();
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement rootElement = JsonParser.parseReader(reader);
                JsonObject object = rootElement.isJsonObject() ? rootElement.getAsJsonObject() : new JsonObject();
                if (!object.has("documentType") || !object.has("data")) {
                    T legacy = gson.fromJson(rootElement, type.getType());
                    return Optional.of(new DocumentEnvelope<>(
                            documentType(namespace), 0, id, 0, Files.getLastModifiedTime(file).toMillis(), legacy
                    ));
                }

                String documentType = object.get("documentType").getAsString();
                int storedVersion = object.get("schemaVersion").getAsInt();
                long revision = object.has("revision") ? object.get("revision").getAsLong() : 0;
                long updatedAt = object.has("updatedAt") ? object.get("updatedAt").getAsLong() : 0;
                JsonElement data = object.get("data");
                if (storedVersion > currentSchemaVersion) {
                    throw new IOException("Unsupported future schema " + storedVersion + " for " + documentType);
                }
                if (storedVersion < currentSchemaVersion && storedVersion > 0) {
                    data = migrations.migrate(documentType, storedVersion, currentSchemaVersion, data);
                    storedVersion = currentSchemaVersion;
                }
                T value = gson.fromJson(data, type.getType());
                return Optional.of(new DocumentEnvelope<>(
                        documentType, storedVersion, id, revision, updatedAt, value
                ));
            } catch (Exception error) {
                quarantine(file);
                throw new IOException("Failed to read " + file + "; corrupt file quarantined", error);
            }
        }
    }

    @Override
    public <T> DocumentEnvelope<T> write(
            String namespace,
            String id,
            TypeToken<T> type,
            int schemaVersion,
            T data
    ) throws IOException {
        synchronized (lock(namespace, id)) {
            Optional<DocumentEnvelope<T>> existing = read(namespace, id, type, schemaVersion);
            long revision = existing.map(DocumentEnvelope::revision).orElse(0L) + 1;
            DocumentEnvelope<T> envelope = new DocumentEnvelope<>(
                    documentType(namespace), schemaVersion, id, revision, System.currentTimeMillis(), data
            );
            atomicWrite(resolve(namespace, id), envelope, type.getType());
            return envelope;
        }
    }

    @Override
    public <T> DocumentEnvelope<T> update(
            String namespace,
            String id,
            TypeToken<T> type,
            int schemaVersion,
            T defaultValue,
            UnaryOperator<T> updater
    ) throws IOException {
        synchronized (lock(namespace, id)) {
            T current = read(namespace, id, type, schemaVersion)
                    .map(DocumentEnvelope::data)
                    .orElse(defaultValue);
            return write(namespace, id, type, schemaVersion, updater.apply(current));
        }
    }

    @Override
    public void unload(String namespace, String id) {
        locks.remove(namespace + ":" + id);
    }

    private Path resolve(String namespace, String id) {
        if (!namespace.matches("[a-z][a-z0-9_-]{0,47}")) {
            throw new IllegalArgumentException("Unsafe datastore namespace: " + namespace);
        }
        Path directory = root.resolve(namespace).normalize();
        if (!directory.startsWith(root)) {
            throw new IllegalArgumentException("Datastore namespace escapes root");
        }
        return ResourceId.resolveJson(directory, id);
    }

    private String documentType(String namespace) {
        return "hycitizens:" + namespace.replace('_', '-');
    }

    private Object lock(String namespace, String id) {
        return locks.computeIfAbsent(namespace + ":" + id, ignored -> new Object());
    }

    private <T> void atomicWrite(Path file, DocumentEnvelope<T> envelope, Type dataType) throws IOException {
        Files.createDirectories(file.getParent());
        rotateBackups(file);
        Path temp = Files.createTempFile(file.getParent(), "." + file.getFileName(), ".tmp");
        JsonObject serialized = new JsonObject();
        serialized.addProperty("documentType", envelope.documentType());
        serialized.addProperty("schemaVersion", envelope.schemaVersion());
        serialized.addProperty("id", envelope.id());
        serialized.addProperty("revision", envelope.revision());
        serialized.addProperty("updatedAt", envelope.updatedAt());
        serialized.add("data", gson.toJsonTree(envelope.data(), dataType));
        try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING)) {
            gson.toJson(serialized, writer);
        }
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private void rotateBackups(Path file) throws IOException {
        if (!Files.exists(file)) {
            return;
        }
        for (int index = MAX_BACKUPS; index >= 1; index--) {
            Path source = index == 1 ? file : file.resolveSibling(file.getFileName() + ".bak" + (index - 1));
            Path target = file.resolveSibling(file.getFileName() + ".bak" + index);
            if (Files.exists(source)) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private void quarantine(Path file) {
        try {
            Path quarantine = root.resolve("quarantine");
            Files.createDirectories(quarantine);
            Files.move(
                    file,
                    quarantine.resolve(file.getFileName() + "." + Instant.now().toEpochMilli() + ".corrupt"),
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (IOException ignored) {
        }
    }
}
