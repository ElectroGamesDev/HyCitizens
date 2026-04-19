package com.electro.hycitizens.map;

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
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class CitizenNpcPortraitResolver {
    private static final Logger LOGGER = Logger.getLogger(CitizenNpcPortraitResolver.class.getName());
    private static final String MEMORIES_PREFIX = "Common/UI/Custom/Pages/Memories/npcs/";
    private static final String PNG_SUFFIX = ".png";
    private static final Set<String> AVAILABLE_PORTRAITS = ConcurrentHashMap.newKeySet();
    private static final Map<String, List<String>> TOKENS_BY_PORTRAIT = new ConcurrentHashMap<>();
    private static final Map<String, String> RESOLVED_BY_MODEL = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> PORTRAIT_BYTES = new ConcurrentHashMap<>();
    private static final Set<String> LOGGED_UNRESOLVED_MODELS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean INDEXED = new AtomicBoolean(false);
    private static final AtomicBoolean MISSING_ASSETS_ZIP_LOGGED = new AtomicBoolean(false);
    private static final Set<String> LEADING_PREFIXES = Set.of("Temple", "Tamed", "Friendly", "Passive", "Companion", "Summoned");
    private static final Set<String> TRAILING_SUFFIXES = Set.of("Model", "Npc", "NPC", "Wander", "Wandering", "Patrol", "Static", "Friendly", "Passive", "Alerted", "Sleeping");
    private static final Map<String, List<String>> MODEL_ALIASES = Map.ofEntries(
            Map.entry("Mosshorn_Plain", List.of("Mosshorn")),
            Map.entry("Bunny", List.of("Bunny", "Rabbit")),
            Map.entry("Rabbit", List.of("Rabbit", "Bunny")),
            Map.entry("Grizzly_Bear", List.of("Bear_Grizzly", "Grizzly", "Bear"))
    );
    private static volatile Path assetsZipPath;

    private CitizenNpcPortraitResolver() {
    }

    @Nullable
    static String resolvePortraitName(@Nullable String modelId) {
        String entryName = resolveEntryName(modelId);
        if (entryName == null) {
            return null;
        }
        return entryName.substring(MEMORIES_PREFIX.length(), entryName.length() - PNG_SUFFIX.length());
    }

    @Nullable
    static byte[] loadPortraitPngByPortraitName(@Nullable String portraitName) {
        if (portraitName == null || portraitName.isBlank()) {
            return null;
        }

        Path zipPath = resolveAssetsZipPath();
        if (zipPath == null) {
            return null;
        }

        String entryName = MEMORIES_PREFIX + portraitName + PNG_SUFFIX;
        byte[] cached = PORTRAIT_BYTES.get(entryName);
        if (cached != null) {
            return Arrays.copyOf(cached, cached.length);
        }

        try (ZipFile zipFile = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) {
                return null;
            }

            try (InputStream inputStream = zipFile.getInputStream(entry)) {
                byte[] bytes = inputStream.readAllBytes();
                PORTRAIT_BYTES.put(entryName, bytes);
                return Arrays.copyOf(bytes, bytes.length);
            }
        } catch (IOException e) {
            LOGGER.warning("[HyCitizens] Failed to read NPC portrait from Assets.zip: " + e.getMessage());
            return null;
        }
    }

    @Nullable
    private static String resolveEntryName(@Nullable String modelId) {
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

        String resolved = findEntryName(modelId);
        if (resolved == null && LOGGED_UNRESOLVED_MODELS.add(modelId)) {
            LOGGER.info("[HyCitizens] No official NPC portrait match for model: " + modelId);
        }

        RESOLVED_BY_MODEL.put(modelId, resolved != null ? resolved : "");
        return resolved;
    }

    @Nullable
    private static String findEntryName(@Nonnull String modelId) {
        for (String candidate : buildCandidates(modelId)) {
            if (AVAILABLE_PORTRAITS.contains(candidate)) {
                return MEMORIES_PREFIX + candidate + PNG_SUFFIX;
            }
        }

        String bestMatch = findBestFuzzyPortrait(modelId);
        return bestMatch != null ? MEMORIES_PREFIX + bestMatch + PNG_SUFFIX : null;
    }

    @Nonnull
    private static List<String> buildCandidates(@Nonnull String modelId) {
        String normalized = normalizeModel(modelId);
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(normalized);

        List<String> aliases = MODEL_ALIASES.get(normalized);
        if (aliases != null) {
            candidates.addAll(aliases);
        }

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

    private static void ensureIndexed() {
        if (!INDEXED.compareAndSet(false, true)) {
            return;
        }

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

                String name = entry.getName();
                if (name.startsWith(MEMORIES_PREFIX) && name.endsWith(PNG_SUFFIX)) {
                    String portraitName = name.substring(MEMORIES_PREFIX.length(), name.length() - PNG_SUFFIX.length());
                    if (!portraitName.isBlank()) {
                        AVAILABLE_PORTRAITS.add(portraitName);
                        TOKENS_BY_PORTRAIT.put(portraitName, normalizedTokens(portraitName));
                    }
                }
            }
            LOGGER.info("[HyCitizens] Indexed official NPC portraits: " + AVAILABLE_PORTRAITS.size());
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
