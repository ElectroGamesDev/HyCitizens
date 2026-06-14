package com.electro.hycitizens.util;

import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;

import javax.annotation.Nonnull;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.ArrayList;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public final class GeneratedAssetReloader {
    private GeneratedAssetReloader() {
    }

    public static boolean reloadNpcBuilderFile(@Nonnull Path path) {
        try {
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null || npcPlugin.getBuilderManager() == null) {
                return false;
            }

            BuilderManager builderManager = npcPlugin.getBuilderManager();
            int index = builderManager.loadFile(path, false, new ArrayList<>());
            if (index == Integer.MIN_VALUE) {
                return false;
            }

            BuilderInfo builderInfo = builderManager.tryGetBuilderInfo(index);
            if (builderInfo != null) {
                BuilderManager.onBuilderReloaded(builderInfo);
            }
            return true;
        } catch (Throwable throwable) {
            getLogger().atWarning().log("[HyCitizens] Could not reload generated NPC builder '" + path + "': " + throwable.getMessage());
            return false;
        }
    }

    public static boolean removeNpcBuilder(@Nonnull String builderName) {
        try {
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null || npcPlugin.getBuilderManager() == null) {
                return false;
            }

            Method removeBuilder = findRemoveBuilderMethod(npcPlugin.getBuilderManager().getClass());
            if (removeBuilder == null) {
                return false;
            }

            removeBuilder.setAccessible(true);
            removeBuilder.invoke(npcPlugin.getBuilderManager(), builderName);
            return true;
        } catch (Throwable throwable) {
            getLogger().atWarning().log("[HyCitizens] Could not remove generated NPC builder '" + builderName + "': " + throwable.getMessage());
            return false;
        }
    }

    public static boolean reloadAssetMapEntry(@Nonnull Object assetMap,
                                              @Nonnull Object assetCodec,
                                              @Nonnull String packKey,
                                              @Nonnull java.util.Map<String, Object> assets,
                                              @Nonnull java.util.Map<String, Path> paths) {
        try {
            Method putAll = findPutAllMethod(assetMap.getClass());
            if (putAll == null) {
                return false;
            }

            putAll.setAccessible(true);
            putAll.invoke(assetMap, packKey, assetCodec, assets, paths, java.util.Collections.emptyMap());
            return true;
        } catch (Throwable throwable) {
            getLogger().atWarning().log("[HyCitizens] Could not reload generated asset map entry: " + throwable.getMessage());
            return false;
        }
    }

    private static Method findPutAllMethod(@Nonnull Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if ("putAll".equals(method.getName()) && method.getParameterCount() == 5) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static Method findRemoveBuilderMethod(@Nonnull Class<?> type) {
        Class<?> current = type;
        while (current != null) {
            for (Method method : current.getDeclaredMethods()) {
                if ("removeBuilder".equals(method.getName())
                        && method.getParameterCount() == 1
                        && method.getParameterTypes()[0] == String.class) {
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }
}
