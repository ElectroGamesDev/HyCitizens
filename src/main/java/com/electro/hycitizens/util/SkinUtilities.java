package com.electro.hycitizens.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.PlayerSkin;
import com.hypixel.hytale.server.core.NameMatching;
import com.hypixel.hytale.server.core.asset.type.model.config.ModelAsset;
import com.hypixel.hytale.server.core.modules.entity.player.PlayerSkinComponent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.playerdata.PlayerStorage;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import com.hypixel.hytale.server.core.cosmetics.CosmeticsModule;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinGradientSet;
import com.hypixel.hytale.server.core.cosmetics.PlayerSkinPart;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class SkinUtilities {

    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public static class CosmeticOptionEntry {
        public final String partId;
        public final List<String> colorOptions;

        public CosmeticOptionEntry(String partId, List<String> colorOptions) {
            this.partId = partId;
            this.colorOptions = Collections.unmodifiableList(colorOptions);
        }
    }

    @Nonnull
    public static Map<String, List<CosmeticOptionEntry>> buildCosmeticCatalogue() {
        CosmeticsModule module = CosmeticsModule.get();
        var reg = module.getRegistry();

        Map<String, List<CosmeticOptionEntry>> catalogue = new LinkedHashMap<>();

        catalogue.put("bodyCharacteristic", buildEntries(reg.getBodyCharacteristics(), reg));
        catalogue.put("underwear",          buildEntries(reg.getUnderwear(),            reg));
        catalogue.put("skinFeature",        buildEntries(reg.getSkinFeatures(),         reg));
        catalogue.put("face",               buildEntries(reg.getFaces(),                reg));
        catalogue.put("eyes",               buildEntries(reg.getEyes(),                 reg));
        catalogue.put("ears",               buildEntries(reg.getEars(),                 reg));
        catalogue.put("mouth",              buildEntries(reg.getMouths(),               reg));
        catalogue.put("eyebrows",           buildEntries(reg.getEyebrows(),             reg));
        catalogue.put("facialHair",         buildEntries(reg.getFacialHairs(),          reg));
        catalogue.put("haircut",            buildEntries(reg.getHaircuts(),             reg));
        catalogue.put("pants",              buildEntries(reg.getPants(),                reg));
        catalogue.put("overpants",          buildEntries(reg.getOverpants(),            reg));
        catalogue.put("undertop",           buildEntries(reg.getUndertops(),            reg));
        catalogue.put("overtop",            buildEntries(reg.getOvertops(),             reg));
        catalogue.put("shoes",              buildEntries(reg.getShoes(),                reg));
        catalogue.put("gloves",             buildEntries(reg.getGloves(),               reg));
        catalogue.put("headAccessory",      buildEntries(reg.getHeadAccessories(),      reg));
        catalogue.put("faceAccessory",      buildEntries(reg.getFaceAccessories(),      reg));
        catalogue.put("earAccessory",       buildEntries(reg.getEarAccessories(),       reg));
        catalogue.put("cape",               buildEntries(reg.getCapes(),                reg));

        return catalogue;
    }

    @Nonnull
    private static List<CosmeticOptionEntry> buildEntries(
            @Nonnull Map<String, PlayerSkinPart> map,
            @Nonnull com.hypixel.hytale.server.core.cosmetics.CosmeticRegistry reg) {

        List<CosmeticOptionEntry> entries = new ArrayList<>();

        for (PlayerSkinPart part : map.values()) {
            String partId = part.getId();
            List<String> colorOptions = new ArrayList<>();

            // Collect the base colour pool (gradient set OR empty)
            List<String> gradientColors = new ArrayList<>();
            if (part.getGradientSet() != null) {
                PlayerSkinGradientSet gradSet =
                        (PlayerSkinGradientSet) reg.getGradientSets().get(part.getGradientSet());
                if (gradSet != null) {
                    gradientColors.addAll(gradSet.getGradients().keySet());
                }
            }

            if (part.getVariants() != null && !part.getVariants().isEmpty()) {
                // Parts that have variants: each variant has its own texture map.
                for (Map.Entry<String, PlayerSkinPart.Variant> ve : part.getVariants().entrySet()) {
                    String variantId = ve.getKey();
                    Map<String, ?> textures = ve.getValue().getTextures();

                    // Build combined colour list: gradient colours + variant texture ids
                    Set<String> allColors = new LinkedHashSet<>(gradientColors);
                    if (textures != null) allColors.addAll(textures.keySet());

                    for (String colorId : allColors) {
                        colorOptions.add(partId + "." + colorId + "." + variantId);
                    }
                }
            } else {
                // No variants: textures live directly on the part.
                Map<String, ?> textures = part.getTextures();

                Set<String> allColors = new LinkedHashSet<>(gradientColors);
                if (textures != null) allColors.addAll(textures.keySet());

                if (allColors.isEmpty()) {
                    colorOptions.add(partId);
                } else {
                    for (String colorId : allColors) {
                        colorOptions.add(partId + "." + colorId);
                    }
                }
            }

            if (!colorOptions.isEmpty()) {
                entries.add(new CosmeticOptionEntry(partId, colorOptions));
            }
        }

        return entries;
    }

    @Nonnull
    public static CompletableFuture<PlayerSkin> getSkin(@Nonnull String username) {
        return fetchSkinInternal(username, false);
    }

    @Nullable
    private static PlayerSkin getOnlinePlayerSkin(@Nonnull String username) {
        try {
            PlayerRef playerRef = Universe.get().getPlayer(username, NameMatching.EXACT_IGNORE_CASE);
            if (playerRef == null) {
                return null;
            }

            Ref<EntityStore> ref = playerRef.getReference();
            if (ref != null && ref.isValid()) {
                PlayerSkinComponent skinComponent = ref.getStore().getComponent(ref, PlayerSkinComponent.getComponentType());
                if (skinComponent != null) {
                    return skinComponent.getPlayerSkin();
                }
            }
        } catch (Exception e) {
            return null;
        }

        return null;
    }

    @Nonnull
    public static CompletableFuture<PlayerSkin> refreshSkin(@Nonnull String username) {
        return fetchSkinInternal(username, true);
    }

    @Nonnull
    private static CompletableFuture<PlayerSkin> fetchSkinInternal(@Nonnull String username, boolean forceApi) {
        if (username.isBlank()) {
            return CompletableFuture.completedFuture(null);
        }

        getLogger().atInfo().log("[HyCitizens] " + (forceApi ? "Refreshing" : "Fetching") + " skin for '" + username + "'...");

        if (!forceApi) {
            PlayerSkin onlineSkin = getOnlinePlayerSkin(username);
            if (onlineSkin != null) {
                getLogger().atInfo().log("[HyCitizens] Found skin from online player.");
                return CompletableFuture.completedFuture(onlineSkin);
            }
        }

        return fetchFromHytlSkin(username, forceApi)
                .thenCompose(result -> {
                    if (result != null) {
                        return CompletableFuture.completedFuture(result);
                    }
                    getLogger().atInfo().log("[HyCitizens] Hytl.skin failed, falling back to PlayerDB...");
                    return fetchFromPlayerDB(username, forceApi);
                });
    }

    @Nonnull
    private static CompletableFuture<PlayerSkin> fetchFromHytlSkin(@Nonnull String username, boolean forceApi) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.hytl.skin/character/" + username))
                .header("User-Agent", "Hytale-Plugin-HyCitizens/1.0")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        getLogger().atWarning().log("[HyCitizens] Hytl.skin API Error: " + response.statusCode());
                        return CompletableFuture.completedFuture(null);
                    }

                    try {
                        JsonObject skinData = JsonParser.parseString(response.body()).getAsJsonObject();

                        if (skinData.has("message") && skinData.get("message").getAsString().contains("profile not found")) {
                            getLogger().atWarning().log("[HyCitizens] User '" + username + "' not found in Hytl.skin.");
                            return CompletableFuture.completedFuture(null);
                        }

                        if (skinData.size() == 0) {
                            getLogger().atWarning().log("[HyCitizens] User '" + username + "' not found in Hytl.skin.");
                            return CompletableFuture.completedFuture(null);
                        }

                        PlayerSkin apiSkin = parseSkinFromHytlSkin(skinData);

                        if (forceApi) {
                            if (apiSkin != null) {
                                getLogger().atInfo().log("[HyCitizens] Refreshed skin from Hytl.skin API.");
                                return CompletableFuture.completedFuture(apiSkin);
                            }
                        } else {
                            return getUuidFromPlayerDB(username).thenCompose(uuid -> {
                                if (uuid != null) {
                                    return getSkinByUuid(uuid).thenApply(localSkin -> {
                                        if (localSkin != null) {
                                            getLogger().atInfo().log("[HyCitizens] Found local skin, ignoring API.");
                                            return localSkin;
                                        } else {
                                            return apiSkin;
                                        }
                                    });
                                } else {
                                    return CompletableFuture.completedFuture(apiSkin);
                                }
                            });
                        }

                        return CompletableFuture.completedFuture(apiSkin);

                    } catch (Exception e) {
                        getLogger().atWarning().log("[HyCitizens] Error parsing Hytl.skin response: " + e.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .exceptionally(e -> {
                    getLogger().atWarning().log("[HyCitizens] Hytl.skin network failed for '" + username + "': " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    return null;
                });
    }

    @Nonnull
    private static CompletableFuture<PlayerSkin> fetchFromPlayerDB(@Nonnull String username, boolean forceApi) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://playerdb.co/api/player/hytale/" + username))
                .header("User-Agent", "Hytale-Plugin-HyCitizens/1.0")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenCompose(response -> {
                    if (response.statusCode() != 200) {
                        getLogger().atWarning().log("[HyCitizens] PlayerDB API Error: " + response.statusCode());
                        return CompletableFuture.completedFuture(null);
                    }

                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();

                        if (!root.get("success").getAsBoolean()) {
                            getLogger().atWarning().log("[HyCitizens] User '" + username + "' not found in PlayerDB.");
                            return CompletableFuture.completedFuture(null);
                        }

                        JsonObject data = root.getAsJsonObject("data");
                        JsonObject player = data.getAsJsonObject("player");

                        UUID uuid = UUID.fromString(player.get("id").getAsString());
                        PlayerSkin apiSkin = parseSkinFromPlayerDB(player);

                        if (forceApi) {
                            if (apiSkin != null) {
                                getLogger().atInfo().log("[HyCitizens] Refreshed skin from PlayerDB API.");
                                return CompletableFuture.completedFuture(apiSkin);
                            }
                        } else {
                            return getSkinByUuid(uuid).thenApply(localSkin -> {
                                if (localSkin != null) {
                                    getLogger().atInfo().log("[HyCitizens] Found local skin, ignoring API.");
                                    return localSkin;
                                } else {
                                    return apiSkin;
                                }
                            });
                        }

                        return getSkinByUuid(uuid).thenApply(skin -> skin);

                    } catch (Exception e) {
                        getLogger().atWarning().log("[HyCitizens] Error parsing PlayerDB skin: " + e.getMessage());
                        return CompletableFuture.completedFuture(null);
                    }
                })
                .exceptionally(e -> {
                    getLogger().atWarning().log("[HyCitizens] PlayerDB network failed for '" + username + "': " + (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
                    return null;
                });
    }

    @Nonnull
    private static CompletableFuture<UUID> getUuidFromPlayerDB(@Nonnull String username) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://playerdb.co/api/player/hytale/" + username))
                .header("User-Agent", "Hytale-Plugin-HyCitizens/1.0")
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        return null;
                    }

                    try {
                        JsonObject root = JsonParser.parseString(response.body()).getAsJsonObject();
                        if (!root.get("success").getAsBoolean()) {
                            return null;
                        }

                        JsonObject data = root.getAsJsonObject("data");
                        JsonObject player = data.getAsJsonObject("player");
                        return UUID.fromString(player.get("id").getAsString());
                    } catch (Exception e) {
                        return null;
                    }
                })
                .exceptionally(e -> null);
    }

    @Nonnull
    public static CompletableFuture<PlayerSkin> getSkinByUuid(@Nonnull UUID uuid) {
        PlayerStorage playerStorage = Universe.get().getPlayerStorage();

        return playerStorage.load(uuid)
                .thenApply(entityStore -> {
                    if (entityStore == null) return null;

                    PlayerSkinComponent skinComponent = entityStore.getComponent(PlayerSkinComponent.getComponentType());
                    return skinComponent != null ? skinComponent.getPlayerSkin() : null;
                })
                .exceptionally(e -> {
                    getLogger().atWarning().log("[HyCitizens] Disk load failed: " + e.getMessage());
                    return null;
                });
    }

    @Nullable
    private static PlayerSkin parseSkinFromHytlSkin(@Nonnull JsonObject skinData) {
        return new PlayerSkin(
                getJsonString(skinData, "bodyCharacteristic"),
                getJsonString(skinData, "underwear"),
                getJsonString(skinData, "face"),
                getJsonString(skinData, "eyes"),
                getJsonString(skinData, "ears"),
                getJsonString(skinData, "mouth"),
                getJsonString(skinData, "facialHair"),
                getJsonString(skinData, "haircut"),
                getJsonString(skinData, "eyebrows"),
                getJsonString(skinData, "pants"),
                getJsonString(skinData, "overpants"),
                getJsonString(skinData, "undertop"),
                getJsonString(skinData, "overtop"),
                getJsonString(skinData, "shoes"),
                getJsonString(skinData, "headAccessory"),
                getJsonString(skinData, "faceAccessory"),
                getJsonString(skinData, "earAccessory"),
                getJsonString(skinData, "skinFeature"),
                getJsonString(skinData, "gloves"),
                getJsonString(skinData, "cape")
        );
    }

    @Nullable
    private static PlayerSkin parseSkinFromPlayerDB(@Nonnull JsonObject playerJson) {
        if (!playerJson.has("skin") || playerJson.get("skin").isJsonNull()) {
            return null;
        }

        JsonObject skin = playerJson.getAsJsonObject("skin");

        return new PlayerSkin(
                getJsonString(skin, "bodyCharacteristic"),
                getJsonString(skin, "underwear"),
                getJsonString(skin, "face"),
                getJsonString(skin, "eyes"),
                getJsonString(skin, "ears"),
                getJsonString(skin, "mouth"),
                getJsonString(skin, "facialHair"),
                getJsonString(skin, "haircut"),
                getJsonString(skin, "eyebrows"),
                getJsonString(skin, "pants"),
                getJsonString(skin, "overpants"),
                getJsonString(skin, "undertop"),
                getJsonString(skin, "overtop"),
                getJsonString(skin, "shoes"),
                getJsonString(skin, "headAccessory"),
                getJsonString(skin, "faceAccessory"),
                getJsonString(skin, "earAccessory"),
                getJsonString(skin, "skinFeature"),
                getJsonString(skin, "gloves"),
                getJsonString(skin, "cape")
        );
    }

    @Nullable
    private static String getJsonString(@Nonnull JsonObject object, @Nonnull String key) {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        return object.get(key).getAsString();
    }

    @Nonnull
    public static PlayerSkin createDefaultSkin() {
        PlayerSkin skin = new PlayerSkin();
        skin.bodyCharacteristic = "human_male";
        skin.underwear = "underwear_male";
        skin.face = "face_a";
        skin.eyes = "eyes_male";
        skin.ears = "ears_a";
        skin.mouth = "mouth_a";
        skin.haircut = "hair_short_messy";
        skin.eyebrows = "eyebrows_thick";
        skin.pants = "pants_shorts_denim";
        skin.undertop = "shirt_tshirt";
        skin.shoes = "shoes_sneakers";
        return skin;
    }

    public static boolean isValidSkin(@Nullable PlayerSkin skin) {
        if (skin == null) {
            return false;
        }

        try {
            CosmeticsModule.get().validateSkin(skin);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean trySetSkinField(@Nonnull PlayerSkin skin, @Nonnull String slotName, @Nullable String value) {
        PlayerSkin candidate = copySkin(skin);
        setSkinField(candidate, slotName, value);
        if (!isValidSkin(candidate)) {
            return false;
        }

        setSkinField(skin, slotName, value);
        return true;
    }

    public static final String[] SLOT_NAMES = {
            "bodyCharacteristic", "underwear", "skinFeature",
            "face", "eyes", "ears", "mouth", "eyebrows", "facialHair",
            "haircut",
            "pants", "overpants", "undertop", "overtop", "shoes", "gloves",
            "headAccessory", "faceAccessory", "earAccessory", "cape"
    };

    public static final String[][] SLOT_CATEGORIES = {
            {"Body", "bodyCharacteristic", "underwear", "skinFeature"},
            {"Face", "face", "eyes", "ears", "mouth", "eyebrows", "facialHair"},
            {"Hair", "haircut"},
            {"Clothing", "pants", "overpants", "undertop", "overtop", "shoes", "gloves"},
            {"Accessories", "headAccessory", "faceAccessory", "earAccessory", "cape"}
    };

    @Nonnull
    public static String partIdOf(@Nonnull String fullId) {
        int dot = fullId.indexOf('.');
        return dot < 0 ? fullId : fullId.substring(0, dot);
    }

    @Nonnull
    public static PlayerSkin copySkin(@Nonnull PlayerSkin source) {
        return new PlayerSkin(
                source.bodyCharacteristic,
                source.underwear,
                source.face,
                source.eyes,
                source.ears,
                source.mouth,
                source.facialHair,
                source.haircut,
                source.eyebrows,
                source.pants,
                source.overpants,
                source.undertop,
                source.overtop,
                source.shoes,
                source.headAccessory,
                source.faceAccessory,
                source.earAccessory,
                source.skinFeature,
                source.gloves,
                source.cape
        );
    }

    @Nullable
    public static String getSkinField(@Nonnull PlayerSkin skin, @Nonnull String slotName) {
        return switch (slotName) {
            case "bodyCharacteristic" -> skin.bodyCharacteristic;
            case "underwear" -> skin.underwear;
            case "skinFeature" -> skin.skinFeature;
            case "face" -> skin.face;
            case "eyes" -> skin.eyes;
            case "ears" -> skin.ears;
            case "mouth" -> skin.mouth;
            case "eyebrows" -> skin.eyebrows;
            case "facialHair" -> skin.facialHair;
            case "haircut" -> skin.haircut;
            case "pants" -> skin.pants;
            case "overpants" -> skin.overpants;
            case "undertop" -> skin.undertop;
            case "overtop" -> skin.overtop;
            case "shoes" -> skin.shoes;
            case "gloves" -> skin.gloves;
            case "headAccessory" -> skin.headAccessory;
            case "faceAccessory" -> skin.faceAccessory;
            case "earAccessory" -> skin.earAccessory;
            case "cape" -> skin.cape;
            default -> null;
        };
    }

    public static void setSkinField(@Nonnull PlayerSkin skin, @Nonnull String slotName, @Nullable String value) {
        switch (slotName) {
            case "bodyCharacteristic" -> skin.bodyCharacteristic = value;
            case "underwear" -> skin.underwear = value;
            case "skinFeature" -> skin.skinFeature = value;
            case "face" -> skin.face = value;
            case "eyes" -> skin.eyes = value;
            case "ears" -> skin.ears = value;
            case "mouth" -> skin.mouth = value;
            case "eyebrows" -> skin.eyebrows = value;
            case "facialHair" -> skin.facialHair = value;
            case "haircut" -> skin.haircut = value;
            case "pants" -> skin.pants = value;
            case "overpants" -> skin.overpants = value;
            case "undertop" -> skin.undertop = value;
            case "overtop" -> skin.overtop = value;
            case "shoes" -> skin.shoes = value;
            case "gloves" -> skin.gloves = value;
            case "headAccessory" -> skin.headAccessory = value;
            case "faceAccessory" -> skin.faceAccessory = value;
            case "earAccessory" -> skin.earAccessory = value;
            case "cape" -> skin.cape = value;
        }
    }

    @Nonnull
    public static String slotDisplayName(@Nonnull String slotName) {
        return switch (slotName) {
            case "bodyCharacteristic" -> "Body Type";
            case "underwear" -> "Underwear";
            case "skinFeature" -> "Skin Feature";
            case "face" -> "Face";
            case "eyes" -> "Eyes";
            case "ears" -> "Ears";
            case "mouth" -> "Mouth";
            case "eyebrows" -> "Eyebrows";
            case "facialHair" -> "Facial Hair";
            case "haircut" -> "Haircut";
            case "pants" -> "Pants";
            case "overpants" -> "Overpants";
            case "undertop" -> "Undertop";
            case "overtop" -> "Overtop";
            case "shoes" -> "Shoes";
            case "gloves" -> "Gloves";
            case "headAccessory" -> "Head Acc.";
            case "faceAccessory" -> "Face Acc.";
            case "earAccessory" -> "Ear Acc.";
            case "cape" -> "Cape";
            default -> slotName;
        };
    }
}
