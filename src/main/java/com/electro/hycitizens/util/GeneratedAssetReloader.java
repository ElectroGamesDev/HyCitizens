package com.electro.hycitizens.util;

import com.hypixel.hytale.server.npc.NPCPlugin;
import com.hypixel.hytale.server.npc.asset.builder.BuilderInfo;
import com.hypixel.hytale.server.npc.asset.builder.BuilderManager;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public final class GeneratedAssetReloader {
    private static final Path TEMP_ROLES_DIR = Paths.get("mods", "HyCitizensData", "tmp", "roles");
    private static final Path TEMP_ATTITUDES_DIR = Paths.get("mods", "HyCitizensData", "tmp", "attitudes");
    private static final Path TEMP_GROUPS_DIR = Paths.get("mods", "HyCitizensData", "tmp", "groups");
    private static final Map<String, Path> roleFiles = new ConcurrentHashMap<>();
    private static final Map<String, Path> attitudeFiles = new ConcurrentHashMap<>();
    private static final Map<String, Path> groupFiles = new ConcurrentHashMap<>();

    private GeneratedAssetReloader() {
    }

    static {
        try {
            cleanup();
            Files.createDirectories(TEMP_ROLES_DIR);
            Files.createDirectories(TEMP_ATTITUDES_DIR);
            Files.createDirectories(TEMP_GROUPS_DIR);
        } catch (IOException e) {
            getLogger().atWarning().log("[HyCitizens] Failed to create temp directories: " + e.getMessage());
        }
    }

    public static void cleanup() {
        cleanupDirectory(roleFiles, TEMP_ROLES_DIR);
        cleanupDirectory(attitudeFiles, TEMP_ATTITUDES_DIR);
        cleanupDirectory(groupFiles, TEMP_GROUPS_DIR);
    }

    private static void cleanupDirectory(Map<String, Path> fileMap, Path directory) {
        fileMap.values().forEach(path -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
            }
        });
        fileMap.clear();

        try {
            if (Files.exists(directory)) {
                Files.walk(directory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException ignored) {
                        }
                    });
                Files.deleteIfExists(directory);
            }
        } catch (IOException ignored) {
        }
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

    public static boolean registerAttitudeFromJson(@Nonnull String name, @Nonnull String jsonContent) {
        try {
            Path attitudeFile = TEMP_ATTITUDES_DIR.resolve(name + ".json");
            Files.write(attitudeFile, jsonContent.getBytes(StandardCharsets.UTF_8));
            attitudeFiles.put(name, attitudeFile);
            return true;
        } catch (Throwable throwable) {
            getLogger().atWarning().log("[HyCitizens] Could not register attitude '" + name + "': " + throwable.getMessage());
            return false;
        }
    }

    public static boolean registerNpcGroupFromJson(@Nonnull String name, @Nonnull String jsonContent) {
        try {
            Path groupFile = TEMP_GROUPS_DIR.resolve(name + ".json");
            Files.write(groupFile, jsonContent.getBytes(StandardCharsets.UTF_8));
            groupFiles.put(name, groupFile);
            return true;
        } catch (Throwable throwable) {
            getLogger().atWarning().log("[HyCitizens] Could not register NPC group '" + name + "': " + throwable.getMessage());
            return false;
        }
    }

    public static int registerNpcBuilderFromJson(@Nonnull String builderName,
                                                  @Nonnull String jsonContent,
                                                  boolean allowUpdate) {
        try {
            NPCPlugin npcPlugin = NPCPlugin.get();
            if (npcPlugin == null || npcPlugin.getBuilderManager() == null) {
                return Integer.MIN_VALUE;
            }

            BuilderManager builderManager = npcPlugin.getBuilderManager();

            int existingIndex = builderManager.getIndex(builderName);
            if (existingIndex != Integer.MIN_VALUE && !allowUpdate) {
                return existingIndex;
            }

            Path roleFile = TEMP_ROLES_DIR.resolve(builderName + ".json");
            Files.write(roleFile, jsonContent.getBytes(StandardCharsets.UTF_8));

            ArrayList<String> errors = new ArrayList<>();
            int index = builderManager.loadFile(roleFile, false, errors);

            if (!errors.isEmpty()) {
                getLogger().atSevere().log("[HyCitizens] Validation errors for role '" + builderName + "':");
                for (String error : errors) {
                    getLogger().atSevere().log("  " + error);
                }
            }

            if (index == Integer.MIN_VALUE) {
                return Integer.MIN_VALUE;
            }

            roleFiles.put(builderName, roleFile);

            BuilderInfo builderInfo = builderManager.tryGetBuilderInfo(index);
            if (builderInfo != null) {
                if (!builderManager.validateBuilder(builderInfo)) {
                    getLogger().atWarning().log("[HyCitizens] Role '" + builderName + "' failed validation after loading");
                    return Integer.MIN_VALUE;
                }
                BuilderManager.onBuilderReloaded(builderInfo);
            }

            return index;

        } catch (Throwable throwable) {
            getLogger().atWarning().log("[HyCitizens] Could not register NPC builder '" +
                    builderName + "': " + throwable.getMessage());
            return Integer.MIN_VALUE;
        }
    }
}
