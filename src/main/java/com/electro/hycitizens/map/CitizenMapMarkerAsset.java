package com.electro.hycitizens.map;

import com.electro.hycitizens.models.CitizenData;
import com.hypixel.hytale.common.util.ArrayUtil;
import com.hypixel.hytale.protocol.Packet;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.setup.AssetFinalize;
import com.hypixel.hytale.protocol.packets.setup.AssetInitialize;
import com.hypixel.hytale.protocol.packets.setup.AssetPart;
import com.hypixel.hytale.protocol.packets.setup.RequestCommonAssetsRebuild;
import com.hypixel.hytale.server.core.HytaleServer;
import com.hypixel.hytale.server.core.asset.common.CommonAsset;
import com.hypixel.hytale.server.core.io.PacketHandler;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public final class CitizenMapMarkerAsset {
    public static final String DEFAULT_MARKER_IMAGE = "hycitizens-pin.png";
    private static final String ASSET_PREFIX = "UI/WorldMap/MapMarkers/";
    private static final int ASSET_PACKET_SIZE = 2_621_440;
    private static final int ICON_SIZE = 32;
    private static final int NPC_CONTENT_SCALE_PERCENT = 96;
    private static final long REBUILD_DEBOUNCE_MS = 40L;
    private static final Map<String, MarkerAsset> GENERATED_ASSETS = new ConcurrentHashMap<>();
    private static final Map<UUID, Set<String>> DELIVERED_BY_VIEWER = new ConcurrentHashMap<>();
    private static final Map<UUID, ScheduledFuture<?>> PENDING_REBUILDS = new ConcurrentHashMap<>();

    private CitizenMapMarkerAsset() {
    }

    @Nonnull
    public static String resolveMarkerImage(@Nonnull CitizenData citizen) {
        String markerType = CitizenData.normalizeMapMarkerType(citizen.getMapMarkerType());
        if (CitizenData.MAP_MARKER_TYPE_NPC_TYPE.equals(markerType)) {
            String npcMarker = ensureNpcTypeIcon(citizen);
            if (npcMarker != null) {
                return npcMarker;
            }
        }
        return ensureBuiltInIcon(markerType);
    }

    public static void deliverAssetsToViewer(@Nonnull PlayerRef viewer, @Nonnull Collection<String> imageNames) {
        if (imageNames.isEmpty()) {
            return;
        }

        UUID viewerUuid = viewer.getUuid();
        PacketHandler packetHandler = viewer.getPacketHandler();
        if (viewerUuid == null || packetHandler == null) {
            return;
        }

        Set<String> deliveredAssets = DELIVERED_BY_VIEWER.computeIfAbsent(viewerUuid, ignored -> ConcurrentHashMap.newKeySet());
        LinkedHashMap<String, MarkerAsset> pendingAssets = new LinkedHashMap<>();
        for (String imageName : imageNames) {
            String assetPath = toAssetPath(imageName);
            if (assetPath == null || deliveredAssets.contains(assetPath)) {
                continue;
            }

            MarkerAsset asset = GENERATED_ASSETS.get(assetPath);
            if (asset != null) {
                pendingAssets.putIfAbsent(assetPath, asset);
            }
        }

        if (pendingAssets.isEmpty()) {
            return;
        }

        for (MarkerAsset asset : pendingAssets.values()) {
            MarkerAsset.sendToPlayer(packetHandler, asset);
        }
        deliveredAssets.addAll(pendingAssets.keySet());
        scheduleRebuild(viewerUuid, packetHandler);
    }

    public static void clearViewer(@Nullable UUID viewerUuid) {
        if (viewerUuid == null) {
            return;
        }

        DELIVERED_BY_VIEWER.remove(viewerUuid);
        ScheduledFuture<?> future = PENDING_REBUILDS.remove(viewerUuid);
        if (future != null && !future.isCancelled()) {
            future.cancel(false);
        }
    }

    public static void clearAllViewers() {
        DELIVERED_BY_VIEWER.clear();
        for (ScheduledFuture<?> future : PENDING_REBUILDS.values()) {
            if (future != null && !future.isCancelled()) {
                future.cancel(false);
            }
        }
        PENDING_REBUILDS.clear();
    }

    @Nonnull
    private static String ensureBuiltInIcon(@Nullable String markerType) {
        String normalized = CitizenData.normalizeMapMarkerType(markerType);
        String imageName = switch (normalized) {
            case CitizenData.MAP_MARKER_TYPE_DOT -> "hycitizens-dot.png";
            case CitizenData.MAP_MARKER_TYPE_STAR -> "hycitizens-star.png";
            case CitizenData.MAP_MARKER_TYPE_DIAMOND -> "hycitizens-diamond.png";
            case CitizenData.MAP_MARKER_TYPE_SQUARE -> "hycitizens-square.png";
            case CitizenData.MAP_MARKER_TYPE_QUESTION -> "hycitizens-question.png";
            case CitizenData.MAP_MARKER_TYPE_EXCLAMATION -> "hycitizens-exclamation.png";
            case CitizenData.MAP_MARKER_TYPE_MONEY_SYMBOL -> "hycitizens-money-symbol.png";
            case CitizenData.MAP_MARKER_TYPE_SHOP -> "hycitizens-shop.png";
            case CitizenData.MAP_MARKER_TYPE_TRADER -> "hycitizens-trader.png";
            case CitizenData.MAP_MARKER_TYPE_CHEST -> "hycitizens-chest.png";
            case CitizenData.MAP_MARKER_TYPE_SWORD -> "hycitizens-sword.png";
            case CitizenData.MAP_MARKER_TYPE_SHIELD -> "hycitizens-shield.png";
            case CitizenData.MAP_MARKER_TYPE_HEART -> "hycitizens-heart.png";
            case CitizenData.MAP_MARKER_TYPE_HOME -> "hycitizens-home.png";
            default -> DEFAULT_MARKER_IMAGE;
        };
        ensureGeneratedImage(imageName, () -> createBuiltInMarkerPng(normalized));
        return imageName;
    }

    @Nullable
    private static String ensureNpcTypeIcon(@Nonnull CitizenData citizen) {
        String modelId = citizen.getModelId();
        String portraitName = CitizenNpcPortraitResolver.resolvePortraitName(modelId);
        if (portraitName != null) {
            String imageName = buildImageName("hycitizens-npc", portraitName);
            String resolved = ensureGeneratedImage(imageName, () -> {
                byte[] portraitPng = CitizenNpcPortraitResolver.loadPortraitPngByPortraitName(portraitName);
                return portraitPng != null && portraitPng.length != 0
                        ? createNpcPortraitMarkerPng(portraitPng, ICON_SIZE, NPC_CONTENT_SCALE_PERCENT)
                        : null;
            });
            if (resolved != null) {
                return resolved;
            }
        }

        String fallbackName = buildImageName("hycitizens-npc-generated", modelId);
        return ensureGeneratedImage(fallbackName, () -> createGeneratedNpcMarkerPng(modelId, modelId));
    }

    @Nullable
    private static String ensureGeneratedImage(@Nullable String imageName, @Nonnull Supplier<byte[]> pngFactory) {
        String assetPath = toAssetPath(imageName);
        if (assetPath == null) {
            return null;
        }

        MarkerAsset asset = GENERATED_ASSETS.computeIfAbsent(assetPath, ignored -> {
            byte[] pngBytes = pngFactory.get();
            return pngBytes == null || pngBytes.length == 0 ? null : new MarkerAsset(assetPath, pngBytes);
        });
        return asset != null ? imageName : null;
    }

    @Nullable
    private static String toAssetPath(@Nullable String imageName) {
        if (imageName == null || imageName.isBlank()) {
            return null;
        }
        return ASSET_PREFIX + imageName;
    }

    @Nonnull
    private static String buildImageName(@Nonnull String prefix, @Nullable String key) {
        return prefix + "-" + sanitizeKey(key) + ".png";
    }

    @Nonnull
    private static String sanitizeKey(@Nullable String key) {
        String normalized = key == null ? "unknown" : key.toLowerCase(Locale.ROOT);
        normalized = normalized.replaceAll("[^a-z0-9]+", "-").replaceAll("(^-+)|(-+$)", "");
        return normalized.isBlank() ? "unknown" : normalized;
    }

    private static byte[] createBuiltInMarkerPng(@Nonnull String markerType) {
        try {
            BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            Color fill = switch (markerType) {
                case CitizenData.MAP_MARKER_TYPE_DOT -> new Color(46, 160, 67, 235);
                case CitizenData.MAP_MARKER_TYPE_STAR -> new Color(218, 167, 62, 240);
                case CitizenData.MAP_MARKER_TYPE_DIAMOND -> new Color(88, 166, 255, 235);
                case CitizenData.MAP_MARKER_TYPE_SQUARE -> new Color(163, 113, 247, 235);
                case CitizenData.MAP_MARKER_TYPE_QUESTION -> new Color(88, 166, 255, 240);
                case CitizenData.MAP_MARKER_TYPE_EXCLAMATION -> new Color(248, 81, 73, 240);
                case CitizenData.MAP_MARKER_TYPE_MONEY_SYMBOL -> new Color(46, 160, 67, 240);
                case CitizenData.MAP_MARKER_TYPE_SHOP -> new Color(210, 153, 34, 240);
                case CitizenData.MAP_MARKER_TYPE_TRADER -> new Color(163, 113, 247, 240);
                case CitizenData.MAP_MARKER_TYPE_CHEST -> new Color(181, 113, 48, 240);
                case CitizenData.MAP_MARKER_TYPE_SWORD -> new Color(139, 148, 158, 240);
                case CitizenData.MAP_MARKER_TYPE_SHIELD -> new Color(56, 139, 253, 240);
                case CitizenData.MAP_MARKER_TYPE_HEART -> new Color(218, 54, 73, 240);
                case CitizenData.MAP_MARKER_TYPE_HOME -> new Color(121, 192, 255, 240);
                default -> new Color(21, 124, 118, 235);
            };

            Shape shape = switch (markerType) {
                case CitizenData.MAP_MARKER_TYPE_DOT -> createCircle();
                case CitizenData.MAP_MARKER_TYPE_STAR -> createStar();
                case CitizenData.MAP_MARKER_TYPE_DIAMOND -> createDiamond();
                case CitizenData.MAP_MARKER_TYPE_SQUARE -> new RoundRectangle2D.Float(5, 5, 22, 22, 6, 6);
                case CitizenData.MAP_MARKER_TYPE_QUESTION,
                     CitizenData.MAP_MARKER_TYPE_EXCLAMATION,
                     CitizenData.MAP_MARKER_TYPE_MONEY_SYMBOL,
                     CitizenData.MAP_MARKER_TYPE_SHOP,
                     CitizenData.MAP_MARKER_TYPE_TRADER -> createCircle();
                case CitizenData.MAP_MARKER_TYPE_CHEST -> createChest();
                case CitizenData.MAP_MARKER_TYPE_SWORD -> createSword();
                case CitizenData.MAP_MARKER_TYPE_SHIELD -> createShield();
                case CitizenData.MAP_MARKER_TYPE_HEART -> createHeart();
                case CitizenData.MAP_MARKER_TYPE_HOME -> createHome();
                default -> createPin();
            };

            g.setColor(fill);
            g.fill(shape);
            g.setStroke(new BasicStroke(2.0f));
            g.setColor(new Color(255, 255, 255, 230));
            g.draw(shape);

            if (CitizenData.MAP_MARKER_TYPE_PIN.equals(markerType)) {
                g.setColor(new Color(246, 220, 93, 245));
                g.fillOval(11, 6, 10, 10);
            }

            String symbol = markerSymbol(markerType);
            if (symbol != null) {
                drawCenteredSymbol(g, symbol);
            }
            drawMarkerDetails(g, markerType);

            g.dispose();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create HyCitizens map marker asset", e);
        }
    }

    private static Shape createPin() {
        Path2D pin = new Path2D.Double();
        pin.moveTo(16, 30);
        pin.curveTo(7, 19, 5, 15, 5, 11);
        pin.curveTo(5, 4, 10, 1, 16, 1);
        pin.curveTo(22, 1, 27, 4, 27, 11);
        pin.curveTo(27, 15, 25, 19, 16, 30);
        pin.closePath();
        return pin;
    }

    private static Shape createCircle() {
        return new java.awt.geom.Ellipse2D.Float(5, 5, 22, 22);
    }

    private static Shape createDiamond() {
        Polygon diamond = new Polygon();
        diamond.addPoint(16, 3);
        diamond.addPoint(29, 16);
        diamond.addPoint(16, 29);
        diamond.addPoint(3, 16);
        return diamond;
    }

    private static Shape createShield() {
        Path2D shield = new Path2D.Double();
        shield.moveTo(16, 3);
        shield.lineTo(27, 7);
        shield.lineTo(25, 19);
        shield.curveTo(23, 24, 19, 28, 16, 30);
        shield.curveTo(13, 28, 9, 24, 7, 19);
        shield.lineTo(5, 7);
        shield.closePath();
        return shield;
    }

    private static Shape createChest() {
        return new RoundRectangle2D.Float(5, 9, 22, 18, 4, 4);
    }

    private static Shape createSword() {
        Path2D sword = new Path2D.Double();
        sword.moveTo(22, 3);
        sword.lineTo(26, 7);
        sword.lineTo(16, 18);
        sword.lineTo(18, 20);
        sword.lineTo(15, 23);
        sword.lineTo(13, 21);
        sword.lineTo(8, 27);
        sword.lineTo(5, 24);
        sword.lineTo(11, 19);
        sword.lineTo(9, 17);
        sword.lineTo(12, 14);
        sword.lineTo(14, 16);
        sword.closePath();
        return sword;
    }

    private static Shape createHeart() {
        Path2D heart = new Path2D.Double();
        heart.moveTo(16, 28);
        heart.curveTo(7, 20, 4, 16, 4, 10);
        heart.curveTo(4, 5, 8, 3, 11, 3);
        heart.curveTo(14, 3, 16, 6, 16, 6);
        heart.curveTo(16, 6, 18, 3, 21, 3);
        heart.curveTo(24, 3, 28, 5, 28, 10);
        heart.curveTo(28, 16, 25, 20, 16, 28);
        heart.closePath();
        return heart;
    }

    private static Shape createHome() {
        Path2D home = new Path2D.Double();
        home.moveTo(4, 15);
        home.lineTo(16, 4);
        home.lineTo(28, 15);
        home.lineTo(25, 15);
        home.lineTo(25, 28);
        home.lineTo(8, 28);
        home.lineTo(8, 15);
        home.closePath();
        return home;
    }

    private static Shape createStar() {
        Path2D star = new Path2D.Double();
        double centerX = 16.0;
        double centerY = 16.0;
        for (int i = 0; i < 10; i++) {
            double angle = Math.toRadians(-90 + i * 36);
            double radius = i % 2 == 0 ? 13.0 : 6.0;
            double x = centerX + Math.cos(angle) * radius;
            double y = centerY + Math.sin(angle) * radius;
            if (i == 0) {
                star.moveTo(x, y);
            } else {
                star.lineTo(x, y);
            }
        }
        star.closePath();
        return star;
    }

    @Nullable
    private static String markerSymbol(@Nonnull String markerType) {
        return switch (markerType) {
            case CitizenData.MAP_MARKER_TYPE_QUESTION -> "?";
            case CitizenData.MAP_MARKER_TYPE_EXCLAMATION -> "!";
            case CitizenData.MAP_MARKER_TYPE_MONEY_SYMBOL -> "$";
            case CitizenData.MAP_MARKER_TYPE_SHOP -> "S";
            case CitizenData.MAP_MARKER_TYPE_TRADER -> "T";
            default -> null;
        };
    }

    private static void drawMarkerDetails(@Nonnull Graphics2D g, @Nonnull String markerType) {
        if (CitizenData.MAP_MARKER_TYPE_CHEST.equals(markerType)) {
            g.setColor(new Color(80, 48, 26, 210));
            g.setStroke(new BasicStroke(1.8f));
            g.drawLine(6, 16, 26, 16);
            g.drawLine(16, 10, 16, 27);
            g.setColor(new Color(246, 220, 93, 245));
            g.fillRoundRect(13, 15, 6, 6, 2, 2);
            g.setColor(new Color(255, 255, 255, 80));
            g.drawLine(8, 12, 24, 12);
        } else if (CitizenData.MAP_MARKER_TYPE_SWORD.equals(markerType)) {
            g.setColor(new Color(255, 255, 255, 95));
            g.setStroke(new BasicStroke(1.4f));
            g.drawLine(22, 6, 13, 16);
            g.setColor(new Color(86, 54, 32, 230));
            g.setStroke(new BasicStroke(3.0f));
            g.drawLine(10, 21, 6, 25);
            g.setColor(new Color(246, 220, 93, 230));
            g.setStroke(new BasicStroke(2.2f));
            g.drawLine(10, 17, 16, 23);
        }
    }

    private static void drawCenteredSymbol(@Nonnull Graphics2D g, @Nonnull String symbol) {
        int fontSize = symbol.length() > 1 ? 10 : 19;
        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
        FontMetrics metrics = g.getFontMetrics();
        int textX = (ICON_SIZE - metrics.stringWidth(symbol)) / 2;
        int textY = (ICON_SIZE - metrics.getHeight()) / 2 + metrics.getAscent() - 1;
        g.setColor(new Color(0, 0, 0, 100));
        g.drawString(symbol, textX + 1, textY + 1);
        g.setColor(Color.WHITE);
        g.drawString(symbol, textX, textY);
    }

    private static byte[] createGeneratedNpcMarkerPng(@Nullable String modelId, @Nullable String displayName) {
        try {
            BufferedImage image = new BufferedImage(ICON_SIZE, ICON_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            Color fill = colorFromSeed(modelId);
            g.setColor(new Color(18, 22, 28, 135));
            g.fillOval(4, 5, 24, 24);
            g.setColor(fill);
            g.fillOval(4, 4, 24, 24);
            g.setColor(new Color(255, 255, 255, 45));
            g.fillOval(7, 7, 12, 7);
            g.setColor(fill.darker().darker());
            g.setStroke(new BasicStroke(2.0f));
            g.drawOval(4, 4, 24, 24);

            String text = abbreviation(displayName != null && !displayName.isBlank() ? displayName : modelId);
            int fontSize = text.length() > 1 ? 14 : 17;
            g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            FontMetrics metrics = g.getFontMetrics();
            int textX = (ICON_SIZE - metrics.stringWidth(text)) / 2;
            int textY = (ICON_SIZE - metrics.getHeight()) / 2 + metrics.getAscent();
            g.setColor(new Color(0, 0, 0, 95));
            g.drawString(text, textX + 1, textY + 1);
            g.setColor(Color.WHITE);
            g.drawString(text, textX, textY);
            g.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            return createBuiltInMarkerPng(CitizenData.MAP_MARKER_TYPE_PIN);
        }
    }

    private static byte[] createNpcPortraitMarkerPng(@Nonnull byte[] rawPng, int size, int contentScalePercent) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(rawPng));
            if (src == null) {
                return createGeneratedNpcMarkerPng(null, null);
            }

            BufferedImage cropped = cropToOpaqueBounds(src);
            if (cropped == null) {
                return createGeneratedNpcMarkerPng(null, null);
            }

            int iconSize = Math.max(20, size);
            double fillRatio = Math.max(0.5, Math.min(1.0, contentScalePercent / 100.0));
            int targetSize = Math.max(8, (int) Math.round(iconSize * fillRatio));
            double scale = Math.min((double) targetSize / cropped.getWidth(), (double) targetSize / cropped.getHeight());
            int drawWidth = Math.max(1, (int) Math.round(cropped.getWidth() * scale));
            int drawHeight = Math.max(1, (int) Math.round(cropped.getHeight() * scale));
            int drawX = (iconSize - drawWidth) / 2;
            int drawY = (iconSize - drawHeight) / 2;

            BufferedImage out = new BufferedImage(iconSize, iconSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = out.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(new Color(0, 0, 0, 52));
            g.fillOval(Math.max(1, drawX - 1), Math.max(1, iconSize - Math.max(6, iconSize / 5)),
                    Math.max(8, drawWidth - 2), Math.max(4, iconSize / 8));
            g.drawImage(cropped, drawX, drawY, drawWidth, drawHeight, (ImageObserver) null);
            g.dispose();

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            ImageIO.write(out, "png", output);
            return output.toByteArray();
        } catch (IOException e) {
            return createGeneratedNpcMarkerPng(null, null);
        }
    }

    @Nullable
    private static BufferedImage cropToOpaqueBounds(@Nonnull BufferedImage src) {
        int minX = src.getWidth();
        int minY = src.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int alpha = (src.getRGB(x, y) >>> 24) & 255;
                if (alpha >= 8) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return null;
        }

        int margin = Math.max(1, Math.min(src.getWidth(), src.getHeight()) / 64);
        minX = Math.max(0, minX - margin);
        minY = Math.max(0, minY - margin);
        maxX = Math.min(src.getWidth() - 1, maxX + margin);
        maxY = Math.min(src.getHeight() - 1, maxY + margin);
        return src.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    @Nonnull
    private static Color colorFromSeed(@Nullable String seed) {
        int hash = seed != null ? seed.hashCode() : 0;
        float hue = (float) ((hash & Integer.MAX_VALUE) % 360) / 360.0f;
        return Color.getHSBColor(hue, 0.55f, 0.9f);
    }

    @Nonnull
    private static String abbreviation(@Nullable String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }

        String cleaned = name.replace("\\n", " ").replaceAll("[^\\p{L}\\p{N} ]", " ").trim();
        if (cleaned.isEmpty()) {
            return "?";
        }

        String[] parts = cleaned.split("\\s+");
        if (parts.length >= 2) {
            return (firstCodePoint(parts[0]) + firstCodePoint(parts[1])).toUpperCase(Locale.ROOT);
        }
        return leadingCodePoints(parts[0], 2).toUpperCase(Locale.ROOT);
    }

    @Nonnull
    private static String firstCodePoint(@Nonnull String value) {
        return leadingCodePoints(value, 1);
    }

    @Nonnull
    private static String leadingCodePoints(@Nonnull String value, int count) {
        if (value.isEmpty() || count <= 0) {
            return "";
        }
        int endIndex = value.offsetByCodePoints(0, Math.min(count, value.codePointCount(0, value.length())));
        return value.substring(0, endIndex);
    }

    private static void scheduleRebuild(@Nonnull UUID viewerUuid, @Nonnull PacketHandler packetHandler) {
        ScheduledFuture<?> existing = PENDING_REBUILDS.get(viewerUuid);
        if (existing != null && !existing.isDone()) {
            return;
        }

        ScheduledFuture<?> future = HytaleServer.SCHEDULED_EXECUTOR.schedule(
                () -> {
                    try {
                        packetHandler.writeNoCache(new RequestCommonAssetsRebuild());
                    } finally {
                        PENDING_REBUILDS.remove(viewerUuid);
                    }
                },
                REBUILD_DEBOUNCE_MS,
                TimeUnit.MILLISECONDS
        );
        PENDING_REBUILDS.put(viewerUuid, future);
    }

    private static String computeHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static final class MarkerAsset extends CommonAsset {
        private final byte[] pngBytes;

        private MarkerAsset(@Nonnull String assetPath, @Nonnull byte[] pngBytes) {
            super(assetPath, computeHash(pngBytes), pngBytes);
            this.pngBytes = pngBytes;
        }

        @Override
        protected CompletableFuture<byte[]> getBlob0() {
            return CompletableFuture.completedFuture(pngBytes);
        }

        private static void sendToPlayer(@Nonnull PacketHandler packetHandler, @Nonnull CommonAsset asset) {
            byte[] blob = asset.getBlob().join();
            byte[][] parts = ArrayUtil.split(blob, ASSET_PACKET_SIZE);
            Packet[] packets = new Packet[parts.length + 2];
            packets[0] = new AssetInitialize(asset.toPacket(), blob.length);

            for (int index = 0; index < parts.length; index++) {
                packets[index + 1] = new AssetPart(parts[index]);
            }

            packets[packets.length - 1] = new AssetFinalize();
            for (Packet packet : packets) {
                packetHandler.write((ToClientPacket) packet);
            }
        }
    }
}
