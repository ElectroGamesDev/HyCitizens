package com.electro.hycitizens.persistence;

import com.google.gson.reflect.TypeToken;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface DataStore extends AutoCloseable {
    <T> Optional<DocumentEnvelope<T>> read(
            @Nonnull String namespace,
            @Nonnull String id,
            @Nonnull TypeToken<T> type,
            int currentSchemaVersion
    ) throws IOException;

    <T> DocumentEnvelope<T> write(
            @Nonnull String namespace,
            @Nonnull String id,
            @Nonnull TypeToken<T> type,
            int schemaVersion,
            @Nonnull T data
    ) throws IOException;

    <T> DocumentEnvelope<T> update(
            @Nonnull String namespace,
            @Nonnull String id,
            @Nonnull TypeToken<T> type,
            int schemaVersion,
            @Nonnull T defaultValue,
            @Nonnull UnaryOperator<T> updater
    ) throws IOException;

    void unload(@Nonnull String namespace, @Nonnull String id);

    @Override
    default void close() {
    }
}
