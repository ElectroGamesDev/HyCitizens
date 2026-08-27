package com.electro.hycitizens.persistence;

import javax.annotation.Nonnull;

public record DocumentEnvelope<T>(
        @Nonnull String documentType,
        int schemaVersion,
        @Nonnull String id,
        long revision,
        long updatedAt,
        @Nonnull T data
) {
    public DocumentEnvelope<T> next(T nextData) {
        return new DocumentEnvelope<>(documentType, schemaVersion, id, revision + 1, System.currentTimeMillis(), nextData);
    }
}
