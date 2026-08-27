package com.electro.hycitizens.map;

import com.hypixel.hytale.server.core.asset.common.CommonAsset;

import javax.annotation.Nonnull;
import java.util.concurrent.CompletableFuture;

public class MemoryCommonAsset extends CommonAsset {
    private final byte[] data;

    public MemoryCommonAsset(@Nonnull String name, @Nonnull byte[] data) {
        super(name, data);
        this.data = data;
    }

    @Nonnull
    @Override
    protected CompletableFuture<byte[]> getBlob0() {
        return CompletableFuture.completedFuture(data);
    }

    @Nonnull
    @Override
    public String toString() {
        return "MemoryCommonAsset{name=" + getName() + ", hash=" + getHash() + ", size=" + data.length + "}";
    }
}
