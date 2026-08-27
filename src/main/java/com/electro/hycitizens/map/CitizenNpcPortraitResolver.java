package com.electro.hycitizens.map;

import com.hypixel.hytale.assetstore.AssetPack;
import com.hypixel.hytale.server.core.asset.AssetModule;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class CitizenNpcPortraitResolver {
    private static final Logger LOGGER = Logger.getLogger(CitizenNpcPortraitResolver.class.getName());
    private static final String PNG_SUFFIX = ".png";
    private static final Set<String> AVAILABLE_PORTRAITS = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> ENTRY_PATH_BY_PORTRAIT = new ConcurrentHashMap<>();
    private static final Map<String, List<String>> TOKENS_BY_PORTRAIT = new ConcurrentHashMap<>();
    private static final Map<String, String> RESOLVED_BY_MODEL = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> PORTRAIT_BYTES = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_UNRESOLVED_MODELS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean INDEXED = new AtomicBoolean(false);
    private static final AtomicBoolean MISSING_ASSETS_ZIP_LOGGED = new AtomicBoolean(false);
    private static final Set<String> LEADING_PREFIXES = Set.of("Temple", "Tamed", "Friendly", "Passive", "Companion", "Summoned");
    private static final Set<String> TRAILING_SUFFIXES = Set.of("Model", "Npc", "NPC", "Wander", "Wandering", "Patrol", "Static", "Friendly", "Passive", "Alerted", "Sleeping");
    private static volatile Path assetsZipPath;

    private CitizenNpcPortraitResolver() {
    }

    @Nullable
    static String resolvePortraitName(@Nullable String modelId) {
        return resolveEntryKey(modelId);
    }

    @Nullable
    static byte[] loadPortraitPngByPortraitName(@Nullable String portraitName) {
        if (portraitName == null || portraitName.isBlank()) {
            return null;
        }

        ensureIndexed();
        String entryPath = ENTRY_PATH_BY_PORTRAIT.get(portraitName);
        if (entryPath == null) {
            entryPath = portraitName;
        }

        byte[] cached = PORTRAIT_BYTES.get(entryPath);
        if (cached != null) {
            return Arrays.copyOf(cached, cached.length);
        }

        // Try reading directly from AssetModule if available
        try {
            AssetModule assetModule = AssetModule.get();
            if (assetModule != null) {
                AssetPack basePack = assetModule.getBaseAssetPack();
                if (basePack != null && basePack.getRoot() != null) {
                    Path portraitFile = basePack.getRoot().resolve(entryPath);
                    if (Files.exists(portraitFile)) {
                        byte[] bytes = Files.readAllBytes(portraitFile);
                        PORTRAIT_BYTES.put(entryPath, bytes);
                        return Arrays.copyOf(bytes, bytes.length);
                    }
                }
                for (AssetPack pack : assetModule.getAssetPacks()) {
                    if (pack != null && pack.getRoot() != null) {
                        Path portraitFile = pack.getRoot().resolve(entryPath);
                        if (Files.exists(portraitFile)) {
                            byte[] bytes = Files.readAllBytes(portraitFile);
                            PORTRAIT_BYTES.put(entryPath, bytes);
                            return Arrays.copyOf(bytes, bytes.length);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        Path zipPath = resolveAssetsZipPath();
        if (zipPath == null) {
            return null;
        }

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryPath);
            if (entry == null) {
                return null;
            }

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                byte[] bytes = inputStream.readAllBytes();
                PORTRAIT_BYTES.put(entryPath, bytes);
                return Arrays.copyOf(bytes, bytes.length);
            }
        } catch (IOException e) {
            LOGGER.warning("[HyCitizens] Failed to read NPC portrait from Assets.zip: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static String resolveEntryKey(@Nullable String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return null;
        }

        ensureIndexed();
        if (AVAILABLE_PORTRAITS.isEmpty()) {
            return null;
        }

        String cached = RESOLVED_BY_MODEL.get(modelId);
        if (cached != null) {
            return cached.isEmpty() ? null : cached;
        }

        String resolved = findEntryKey(modelId);
        if (resolved == null && LOGGED_UNRESOLVED_MODELS.add(modelId)) {
            LOGGER.info("[HyCitizens] No official NPC portrait match for model: " + modelId);
        }

        RESOLVED_BY_MODEL.put(modelId, resolved != null ? resolved : "");
        return resolved;
    }

    @Nullable
    private static String findEntryKey(@Nonnull String modelId) {
        for (String candidate : buildCandidates(modelId)) {
            if (AVAILABLE_PORTRAITS.contains(candidate)) {
                return candidate;
            }
        }

        String category = resolveCategoryForModel(modelId);
        if (category != null && AVAILABLE_PORTRAITS.contains(category)) {
            return category;
        }

        return findBestFuzzyPortrait(modelId);
    }

    @Nullable
    private static String resolveCategoryForModel(@Nonnull String modelId) {
        String lower = modelId.toLowerCase(Locale.ROOT);
        if (lower.contains("bear") || lower.contains("wolf") || lower.contains("fox")
                || lower.contains("tiger") || lower.contains("hyena") || lower.contains("lion")
                || lower.contains("emberwulf") || lower.contains("raptor") || lower.contains("rex")) {
            return "Predator";
        }
        if (lower.contains("cow") || lower.contains("bull") || lower.contains("sheep")
                || lower.contains("pig") || lower.contains("chicken") || lower.contains("goat")
                || lower.contains("horse") || lower.contains("donkey") || lower.contains("moose")
                || lower.contains("antelope") || lower.contains("deer") || lower.contains("mosshorn")) {
            return "Livestock";
        }
        if (lower.contains("bunny") || lower.contains("rabbit") || lower.contains("frog")
                || lower.contains("toad") || lower.contains("mouse") || lower.contains("rat")
                || lower.contains("bat") || lower.contains("squirrel") || lower.contains("bird")
                || lower.contains("duck") || lower.contains("pigeon") || lower.contains("penguin")
                || lower.contains("tadpole") || lower.contains("cactee") || lower.contains("snail")
                || lower.contains("grooble") || lower.contains("hatworm") || lower.contains("mushee")) {
            return "Critter";
        }
        if (lower.contains("snake") || lower.contains("lizard") || lower.contains("caiman")
                || lower.contains("crocodile") || lower.contains("alligator") || lower.contains("tortoise")
                || lower.contains("turtle")) {
            return "Reptile";
        }
        if (lower.contains("fish") || lower.contains("piranha") || lower.contains("salmon")
                || lower.contains("trout") || lower.contains("eel")) {
            return "Freshwater";
        }
        if (lower.contains("hawk") || lower.contains("eagle") || lower.contains("owl")
                || lower.contains("crow") || lower.contains("raven") || lower.contains("tetrabird")) {
            return "Avian";
        }
        if (lower.contains("trork")) {
            return "Trork";
        }
        if (lower.contains("kweebec")) {
            return "Kweebec";
        }
        if (lower.contains("goblin")) {
            return "Goblin";
        }
        if (lower.contains("feran")) {
            return "Feran";
        }
        if (lower.contains("outlander")) {
            return "Outlander";
        }
        if (lower.contains("scarak") || lower.contains("larva") || lower.contains("louse") || lower.contains("scorpion")) {
            return "Scarak";
        }
        if (lower.contains("skeleton") || lower.contains("zombie") || lower.contains("werewolf")
                || lower.contains("wraith") || lower.contains("ghost") || lower.contains("mummy") || lower.contains("vampire")) {
            return "Undead";
        }
        if (lower.contains("void") || lower.contains("spawn_void") || lower.contains("crawler_void")
                || lower.contains("eye_void") || lower.contains("spectre_void") || lower.contains("necromancer_void")) {
            return "Voidspawn";
        }
        if (lower.contains("golem") || lower.contains("spark") || lower.contains("elemental")) {
            return "Elemental";
        }
        return null;
    }

    @Nonnull
    private static List<String> buildCandidates(@Nonnull String modelId) {
        String normalized = normalizeModel(modelId);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);

        List<String> parts = new ArrayList<>(Arrays.asList(normalized.split("_")));
        while (!parts.isEmpty() && LEADING_PREFIXES.contains(parts.get(0))) {
            parts.remove(0);
            if (!parts.isEmpty()) {
                candidates.add(String.join("_", parts));
            }
        }

        parts = new ArrayList<>(Arrays.asList(normalized.split("_")));
        while (!parts.isEmpty() && (TRAILING_SUFFIXES.contains(parts.get(parts.size() - 1)) || parts.get(parts.size() - 1).matches("V\\d*"))) {
            parts.remove(parts.size() - 1);
            if (!parts.isEmpty()) {
                candidates.add(String.join("_", parts));
            }
        }

        String[] normalizedParts = normalized.split("_");
        if (normalizedParts.length > 1) {
            for (int start = 1; start < normalizedParts.length; start++) {
                candidates.add(String.join("_", Arrays.copyOfRange(normalizedParts, start, normalizedParts.length)));
            }
            for (int end = normalizedParts.length - 1; end > 0; end--) {
                candidates.add(String.join("_", Arrays.copyOfRange(normalizedParts, 0, end)));
            }
        }

        return List.copyOf(candidates);
    }

    @Nullable
    private static String findBestFuzzyPortrait(@Nonnull String modelId) {
        List<String> modelTokens = normalizedTokens(normalizeModel(modelId));
        if (modelTokens.isEmpty()) {
            return null;
        }

        int bestScore = Integer.MIN_VALUE;
        String bestPortrait = null;
        for (String portraitName : AVAILABLE_PORTRAITS) {
            List<String> portraitTokens = TOKENS_BY_PORTRAIT.get(portraitName);
            if (portraitTokens == null || portraitTokens.isEmpty()) {
                continue;
            }

            int score = scorePortraitMatch(modelTokens, portraitTokens, portraitName);
            if (score > bestScore) {
                bestScore = score;
                bestPortrait = portraitName;
            }
        }

        return bestScore >= 8 ? bestPortrait : null;
    }

    private static int scorePortraitMatch(@Nonnull List<String> modelTokens, @Nonnull List<String> portraitTokens,
                                          @Nonnull String portraitName) {
        int overlap = 0;
        List<String> remaining = new ArrayList<>(portraitTokens);
        for (String token : modelTokens) {
            if (remaining.remove(token)) {
                overlap++;
            }
        }

        if (overlap == 0) {
            return Integer.MIN_VALUE;
        }

        int score = overlap * 10;
        score -= (modelTokens.size() - overlap) * 3;
        score -= (portraitTokens.size() - overlap) * 2;
        String normalizedModel = normalizeModel(String.join("_", modelTokens));
        if (portraitName.equals(normalizedModel)) {
            score += 50;
        } else if (portraitName.endsWith(normalizedModel) || normalizedModel.endsWith(portraitName)) {
            score += 20;
        }
        return score;
    }

    @Nonnull
    private static List<String> normalizedTokens(@Nonnull String name) {
        List<String> tokens = new ArrayList<>();
        for (String token : name.split("_")) {
            if (token != null && !token.isBlank()
                    && !LEADING_PREFIXES.contains(token)
                    && !TRAILING_SUFFIXES.contains(token)
                    && !token.matches("V\\d*")) {
                tokens.add(token.toLowerCase(Locale.ROOT));
            }
        }
        return List.copyOf(tokens);
    }

    @Nonnull
    private static String normalizeModel(@Nonnull String modelId) {
        String camelSplit = modelId.replaceAll("([a-z0-9])([A-Z])", "$1_$2");
        String cleaned = camelSplit.replace('-', '_').replaceAll("[^A-Za-z0-9_]+", "_");
        String[] rawParts = cleaned.split("_+");
        List<String> parts = new ArrayList<>();
        for (String rawPart : rawParts) {
            if (rawPart == null || rawPart.isBlank()) {
                continue;
            }

            String part = rawPart.toLowerCase(Locale.ROOT);
            parts.add(Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return String.join("_", parts);
    }

    private static void registerIndexedPath(@Nonnull String entryPath) {
        if (!entryPath.endsWith(PNG_SUFFIX) || entryPath.contains("/Attachments/") || entryPath.contains("/Weapons/")) {
            return;
        }

        // 1. Common/UI/Custom/Pages/Memories/categories/<Name>@2x.png
        if (entryPath.startsWith("Common/UI/Custom/Pages/Memories/categories/")) {
            String fileName = entryPath.substring(entryPath.lastIndexOf('/') + 1);
            String name = fileName.replaceAll("@2x\\.png$", "").replaceAll("\\.png$", "");
            if (!name.isBlank() && !ENTRY_PATH_BY_PORTRAIT.containsKey(name)) {
                AVAILABLE_PORTRAITS.add(name);
                ENTRY_PATH_BY_PORTRAIT.put(name, entryPath);
                TOKENS_BY_PORTRAIT.put(name, normalizedTokens(name));
            }
        }
        // 2. Common/UI/Custom/Pages/Memories/<Name>.png
        else if (entryPath.startsWith("Common/UI/Custom/Pages/Memories/")) {
            String fileName = entryPath.substring(entryPath.lastIndexOf('/') + 1);
            String name = fileName.replaceAll("@2x\\.png$", "").replaceAll("\\.png$", "");
            if (!name.isBlank() && !ENTRY_PATH_BY_PORTRAIT.containsKey(name)) {
                AVAILABLE_PORTRAITS.add(name);
                ENTRY_PATH_BY_PORTRAIT.put(name, entryPath);
                TOKENS_BY_PORTRAIT.put(name, normalizedTokens(name));
            }
        }
        // 3. Custom mod portraits in UI/WorldMap/MapMarkers/ or Common/UI/
        else if (entryPath.startsWith("UI/WorldMap/MapMarkers/") || entryPath.startsWith("Common/UI/")) {
            String fileName = entryPath.substring(entryPath.lastIndexOf('/') + 1);
            String name = fileName.replaceAll("@2x\\.png$", "").replaceAll("\\.png$", "");
            if (!name.isBlank() && !ENTRY_PATH_BY_PORTRAIT.containsKey(name)) {
                AVAILABLE_PORTRAITS.add(name);
                ENTRY_PATH_BY_PORTRAIT.put(name, entryPath);
                TOKENS_BY_PORTRAIT.put(name, normalizedTokens(name));
            }
        }
    }

    private static void ensureIndexed() {
        if (!INDEXED.compareAndSet(false, true)) {
            return;
        }

        // 1. Try indexing from AssetModule if available
        try {
            AssetModule assetModule = AssetModule.get();
            if (assetModule != null) {
                List<AssetPack> packs = new ArrayList<>();
                if (assetModule.getBaseAssetPack() != null) {
                    packs.add(assetModule.getBaseAssetPack());
                }
                packs.addAll(assetModule.getAssetPacks());

                for (AssetPack pack : packs) {
                    if (pack == null || pack.getRoot() == null) continue;
                    Path npcDir = pack.getRoot().resolve("Common/NPC");
                    if (Files.isDirectory(npcDir)) {
                        try (Stream<Path> stream = Files.walk(npcDir)) {
                            stream.filter(p -> p.toString().endsWith(PNG_SUFFIX)).forEach(p -> {
                                Path rel = pack.getRoot().relativize(p);
                                registerIndexedPath(rel.toString().replace('\\', '/'));
                            });
                        }
                    }
                }
                if (!AVAILABLE_PORTRAITS.isEmpty()) {
                    LOGGER.info("[HyCitizens] Indexed official NPC portraits from AssetModule: " + AVAILABLE_PORTRAITS.size());
                    return;
                }
            }
        } catch (Throwable ignored) {
        }

        // 2. Fallback to indexing from Assets.zip
        Path zipPath = resolveAssetsZipPath();
        if (zipPath == null) {
            return;
        }

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                registerIndexedPath(entry.getName().replace('\\', '/'));
            }
            LOGGER.info("[HyCitizens] Indexed official NPC portraits from Assets.zip: " + AVAILABLE_PORTRAITS.size());
        } catch (IOException e) {
            LOGGER.warning("[HyCitizens] Failed to index official NPC portraits: " + e.getMessage());
        }
    }

    @Nullable
    private static Path resolveAssetsZipPath() {
        Path cached = assetsZipPath;
        if (cached != null && Files.exists(cached, LinkOption.NOFOLLOW_LINKS)) {
            return cached;
        }

        for (Path candidate : buildAssetsZipCandidates()) {
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
                assetsZipPath = candidate;
                return candidate;
            }
        }

        if (MISSING_ASSETS_ZIP_LOGGED.compareAndSet(false, true)) {
            LOGGER.warning("[HyCitizens] Could not locate Hytale Assets.zip for NPC type map markers; generated fallback icons will be used.");
        }
        return null;
    }

    @Nonnull
    private static List<Path> buildAssetsZipCandidates() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();

        try {
            AssetModule assetModule = AssetModule.get();
            if (assetModule != null) {
                if (assetModule.getBaseAssetPack() != null && assetModule.getBaseAssetPack().getPackLocation() != null) {
                    candidates.add(assetModule.getBaseAssetPack().getPackLocation());
                }
                for (AssetPack pack : assetModule.getAssetPacks()) {
                    if (pack != null && pack.getPackLocation() != null) {
                        candidates.add(pack.getPackLocation());
                    }
                }
            }
        } catch (Throwable ignored) {
        }

        Path cwd = Paths.get("").toAbsolutePath().normalize();
        candidates.add(cwd.resolve("Assets.zip"));

        String userDir = System.getProperty("user.dir");
        if (userDir != null && !userDir.isBlank()) {
            Path userDirPath = Paths.get(userDir).toAbsolutePath().normalize();
            candidates.add(userDirPath.resolve("Assets.zip"));

            for (Path path = userDirPath; path != null; path = path.getParent()) {
                candidates.add(path.resolve("Assets.zip"));
                candidates.add(path.resolve("install/release/package/game/latest/Assets.zip"));
                candidates.add(path.resolve("release/package/game/latest/Assets.zip"));
                candidates.add(path.resolve("game/latest/Assets.zip"));
            }
        }

        return List.copyOf(candidates);
    }
}
