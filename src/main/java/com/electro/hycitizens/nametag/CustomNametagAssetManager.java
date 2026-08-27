package com.electro.hycitizens.nametag;

import com.electro.hycitizens.map.MemoryCommonAsset;
import com.hypixel.hytale.server.core.asset.common.CommonAssetModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;

public class CustomNametagAssetManager {
    private static final String ASSET_PACK_NAME = "electro:HyCitizensData";
    private static final String TEXTURE_PATH_PREFIX = "Items/Nametags/";
    private static final String MODEL_PATH_PREFIX = "Items/Nametags/";
    private static final Path CACHE_DIR = Paths.get("mods/HyCitizensData/CustomNametagCache");
    private static final Path COMMON_DIR = Paths.get("mods/HyCitizensData/Common");
    private static final Path SERVER_MODELS_DIR = Paths.get("mods/HyCitizensData/Server/Models/CustomNametags");
    private static final int MAX_CACHE_SIZE = 1000;
    private static final int HOT_RELOAD_FIRST_CHECK_MS = 3000;
    private static final int HOT_RELOAD_RETRY_CHECK_MS = 2000;

    private static final Map<String, String> formatHashToAssetId = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> formatHashToTextureBytes = new ConcurrentHashMap<>();
    private static final Map<String, String> formatHashToModelJson = new ConcurrentHashMap<>();
    private static final Map<String, Path> modelAssetPaths = new ConcurrentHashMap<>();
    private static final Map<String, Runnable> pendingReloadCallbacks = new ConcurrentHashMap<>();
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            return;
        }

        try {
            Files.createDirectories(CACHE_DIR);
            Files.createDirectories(COMMON_DIR);
            Files.createDirectories(SERVER_MODELS_DIR);
            loadCachedAssets();
            initialized = true;
            getLogger().atInfo().log("[HyCitizens] CustomNametagAssetManager initialized, loaded " + formatHashToAssetId.size() + " cached nametag(s)");
        } catch (Exception e) {
            getLogger().atSevere().log("[HyCitizens] Failed to initialize CustomNametagAssetManager: " + e.getMessage());
        }
    }

    private static void loadCachedAssets() {
        try {
            if (!Files.exists(CACHE_DIR)) {
                return;
            }

            try (Stream<Path> paths = Files.list(CACHE_DIR)) {
                paths.filter(path -> path.getFileName().toString().endsWith(".png"))
                        .forEach(texturePath -> {
                            try {
                                String fileName = texturePath.getFileName().toString();
                                String hash = fileName.substring(0, fileName.length() - 4);

                                Path blockymodelPath = CACHE_DIR.resolve(hash + ".blockymodel");
                                Path modelJsonPath = CACHE_DIR.resolve(hash + ".model");
                                Path metaPath = CACHE_DIR.resolve(hash + ".meta");

                                if (!Files.exists(blockymodelPath) || !Files.exists(modelJsonPath) || !Files.exists(metaPath)) {
                                    return;
                                }

                                byte[] textureBytes = Files.readAllBytes(texturePath);
                                String blockymodelJson = new String(Files.readAllBytes(blockymodelPath), StandardCharsets.UTF_8);
                                String itemJson = new String(Files.readAllBytes(modelJsonPath), StandardCharsets.UTF_8);
                                String itemAssetId = new String(Files.readAllBytes(metaPath), StandardCharsets.UTF_8).trim();

                                String texturePath2 = TEXTURE_PATH_PREFIX + hash + ".png";
                                String blockymodelPath2 = MODEL_PATH_PREFIX + hash + ".blockymodel";

                                registerTexture(texturePath2, textureBytes);
                                registerModel(blockymodelPath2, blockymodelJson);
                                Path itemJsonPath2 = writeAssetsToDisk(texturePath2, textureBytes, blockymodelPath2, blockymodelJson, itemAssetId, itemJson);

                                formatHashToAssetId.put(hash, itemAssetId);
                                formatHashToTextureBytes.put(hash, textureBytes);
                                formatHashToModelJson.put(hash, blockymodelJson);
                                if (itemJsonPath2 != null) {
                                    modelAssetPaths.put(itemAssetId, itemJsonPath2);
                                }

                                getLogger().atFine().log("[HyCitizens] Restored cached nametag files: " + itemAssetId);
                            } catch (Exception e) {
                                getLogger().atWarning().log("[HyCitizens] Failed to load cached nametag: " + e.getMessage());
                            }
                        });
            }
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Failed to load cached nametags: " + e.getMessage());
        }
    }

    public static boolean hasFormatCodes(@Nonnull String text) {
        return NametagFormatParser.hasFormatCodes(text);
    }

    @Nullable
    public static String getOrGenerateAssetId(@Nonnull String formattedName) {
        return getOrGenerateAssetIdForLines(Collections.singletonList(formattedName));
    }

    @Nullable
    public static String getOrGenerateAssetIdForLines(@Nonnull List<String> lines) {
        boolean hasFormatting = false;
        for (String line : lines) {
            if (hasFormatCodes(line)) {
                hasFormatting = true;
                break;
            }
        }

        if (!hasFormatting) {
            return null;
        }

        if (formatHashToAssetId.size() >= MAX_CACHE_SIZE) {
            getLogger().atWarning().log("[HyCitizens] CustomNametagAssetManager cache limit reached, clearing oldest entries");
            clearCache();
        }

        StringBuilder combined = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) combined.append("\n");
            combined.append(NametagFormatParser.normalizeForHashing(lines.get(i)));
        }
        String normalized = combined.toString();
        String hash = generateHash(normalized);

        String cachedAssetId = formatHashToAssetId.get(hash);
        if (cachedAssetId != null) {
            return cachedAssetId;
        }

        try {
            byte[] textureBytes = NametagTextureGenerator.generateMultiLineTexture(lines);
            if (textureBytes == null) {
                getLogger().atWarning().log("[HyCitizens] Failed to generate texture for nametag with " + lines.size() + " line(s)");
                return null;
            }

            String texturePath = TEXTURE_PATH_PREFIX + hash + ".png";
            String blockymodelPath = MODEL_PATH_PREFIX + hash + ".blockymodel";

            if (!registerTexture(texturePath, textureBytes)) {
                return null;
            }

            String blockymodelJson = NametagModelGenerator.generateBlockyModel(textureBytes);
            if (!registerModel(blockymodelPath, blockymodelJson)) {
                return null;
            }

            String modelAssetId = "CustomNametag_" + hash;
            String modelAssetJsonContent = NametagItemGenerator.generateModelAssetJson(blockymodelPath, texturePath);

            Path modelAssetJsonPath = writeAssetsToDisk(texturePath, textureBytes, blockymodelPath, blockymodelJson, modelAssetId, modelAssetJsonContent);
            if (modelAssetJsonPath == null) {
                getLogger().atWarning().log("[HyCitizens] Failed to write assets to disk");
                return null;
            }

            if (!saveToDiskCache(hash, modelAssetId, textureBytes, blockymodelJson, modelAssetJsonContent)) {
                getLogger().atWarning().log("[HyCitizens] Failed to cache nametag to disk: " + modelAssetId);
            }

            formatHashToAssetId.put(hash, modelAssetId);
            formatHashToTextureBytes.put(hash, textureBytes);
            formatHashToModelJson.put(hash, blockymodelJson);
            modelAssetPaths.put(modelAssetId, modelAssetJsonPath);

            getLogger().atInfo().log("[HyCitizens] Generated custom nametag ModelAsset: " + modelAssetId);

            scheduleHotReloadCheck(modelAssetId);

            return modelAssetId;
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Failed to generate custom nametag asset for " + lines.size() + " line(s): " + e.getMessage());
            return null;
        }
    }

    private static boolean registerTexture(@Nonnull String texturePath, @Nonnull byte[] textureBytes) {
        try {
            CommonAssetModule commonAssetModule = CommonAssetModule.get();
            if (commonAssetModule == null) {
                getLogger().atWarning().log("[HyCitizens] CommonAssetModule not available");
                return false;
            }

            MemoryCommonAsset asset = new MemoryCommonAsset(texturePath, textureBytes);
            commonAssetModule.addCommonAsset(ASSET_PACK_NAME, asset);
            return true;
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Failed to register texture: " + e.getMessage());
            return false;
        }
    }

    private static boolean registerModel(@Nonnull String modelPath, @Nonnull String modelJson) {
        try {
            CommonAssetModule commonAssetModule = CommonAssetModule.get();
            if (commonAssetModule == null) {
                getLogger().atWarning().log("[HyCitizens] CommonAssetModule not available");
                return false;
            }

            byte[] modelBytes = modelJson.getBytes(StandardCharsets.UTF_8);
            MemoryCommonAsset asset = new MemoryCommonAsset(modelPath, modelBytes);
            commonAssetModule.addCommonAsset(ASSET_PACK_NAME, asset);
            return true;
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Failed to register model: " + e.getMessage());
            return false;
        }
    }


    @Nullable
    private static Path writeAssetsToDisk(@Nonnull String texturePath, @Nonnull byte[] textureBytes,
                                          @Nonnull String blockymodelPath, @Nonnull String blockymodelJson,
                                          @Nonnull String modelAssetId, @Nonnull String modelAssetJson) {
        try {
            Path textureFile = COMMON_DIR.resolve(texturePath);
            Path blockymodelFile = COMMON_DIR.resolve(blockymodelPath);
            Path modelAssetJsonFile = SERVER_MODELS_DIR.resolve(modelAssetId + ".json");

            Files.createDirectories(textureFile.getParent());
            Files.createDirectories(blockymodelFile.getParent());
            Files.createDirectories(modelAssetJsonFile.getParent());

            Files.write(textureFile, textureBytes);
            Files.write(blockymodelFile, blockymodelJson.getBytes(StandardCharsets.UTF_8));
            Files.write(modelAssetJsonFile, modelAssetJson.getBytes(StandardCharsets.UTF_8));

            return modelAssetJsonFile;
        } catch (IOException e) {
            getLogger().atWarning().log("[HyCitizens] Failed to write assets to disk: " + e.getMessage());
            return null;
        }
    }

    private static void scheduleHotReloadCheck(@Nonnull String modelAssetId) {
        HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
            checkModelAssetHotReload(modelAssetId, 0);
        }, HOT_RELOAD_FIRST_CHECK_MS, TimeUnit.MILLISECONDS);
    }

    private static void checkModelAssetHotReload(@Nonnull String modelAssetId, int attempt) {
        ModelAsset modelAsset =
            ModelAsset.getAssetMap().getAsset(modelAssetId);

        if (modelAsset != null) {
            int totalWaitMs = (attempt == 0) ? HOT_RELOAD_FIRST_CHECK_MS : HOT_RELOAD_FIRST_CHECK_MS + (attempt * HOT_RELOAD_RETRY_CHECK_MS);
            getLogger().atInfo().log("[HyCitizens] ModelAsset hot-reloaded successfully after " + totalWaitMs + "ms: " + modelAssetId);

            Runnable callback = pendingReloadCallbacks.remove(modelAssetId);
            if (callback != null) {
                try {
                    callback.run();
                } catch (Exception e) {
                    getLogger().atWarning().log("[HyCitizens] Error in hot-reload callback: " + e.getMessage());
                }
            }
            return;
        }

        if (attempt == 0) {
            HytaleServer.SCHEDULED_EXECUTOR.schedule(() -> {
                checkModelAssetHotReload(modelAssetId, 1);
            }, HOT_RELOAD_RETRY_CHECK_MS, TimeUnit.MILLISECONDS);
        } else {
            getLogger().atWarning().log("[HyCitizens] ModelAsset did not hot-reload after " + (HOT_RELOAD_FIRST_CHECK_MS + HOT_RELOAD_RETRY_CHECK_MS) + "ms: " + modelAssetId);
            pendingReloadCallbacks.remove(modelAssetId);
        }
    }

    public static void registerReloadCallback(@Nonnull String modelAssetId, @Nonnull Runnable callback) {
        pendingReloadCallbacks.put(modelAssetId, callback);
    }


    private static boolean saveToDiskCache(@Nonnull String hash, @Nonnull String itemAssetId, @Nonnull byte[] textureBytes,
                                           @Nonnull String blockymodelJson, @Nonnull String itemJson) {
        try {
            Path texturePath = CACHE_DIR.resolve(hash + ".png");
            Path blockymodelPath = CACHE_DIR.resolve(hash + ".blockymodel");
            Path itemJsonPath = CACHE_DIR.resolve(hash + ".model");
            Path metaPath = CACHE_DIR.resolve(hash + ".meta");

            Files.write(texturePath, textureBytes);
            Files.write(blockymodelPath, blockymodelJson.getBytes(StandardCharsets.UTF_8));
            Files.write(itemJsonPath, itemJson.getBytes(StandardCharsets.UTF_8));
            Files.write(metaPath, itemAssetId.getBytes(StandardCharsets.UTF_8));

            return true;
        } catch (IOException e) {
            getLogger().atWarning().log("[HyCitizens] Failed to save nametag cache: " + e.getMessage());
            return false;
        }
    }

    @Nonnull
    private static String generateHash(@Nonnull String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(Math.abs(input.hashCode()));
        }
    }

    public static void clearCache() {
        formatHashToAssetId.clear();
        formatHashToTextureBytes.clear();
        getLogger().atInfo().log("[HyCitizens] Custom nametag cache cleared");
    }

    @Nonnull
    public static String stripFormatCodes(@Nonnull String text) {
        String stripped = NametagFormatParser.stripFormatCodes(text);
        return stripped != null ? stripped : text;
    }
}
