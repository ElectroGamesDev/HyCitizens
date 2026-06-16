package com.electro.hycitizens.nametag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.font.TextLayout;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.LinearGradientPaint;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hypixel.hytale.logger.HytaleLogger.getLogger;

public class NametagTextureGenerator {
    private static final int FONT_SIZE = 96;
    private static final int PADDING = 24;
    private static final int OUTLINE_THICKNESS = 2;
    private static Font BASE_FONT_REGULAR;
    private static Font BASE_FONT_BOLD;

    static {
        try {
            InputStream regularStream = NametagTextureGenerator.class.getResourceAsStream("/fonts/NotoSans-Regular.ttf");
            if (regularStream != null) {
                Font loadedFont = Font.createFont(Font.TRUETYPE_FONT, regularStream);
                BASE_FONT_REGULAR = loadedFont.deriveFont(Font.PLAIN, FONT_SIZE);
                regularStream.close();
            } else {
                getLogger().atWarning().log("[HyCitizens] NotoSans-Regular.ttf not found, falling back to SansSerif");
                BASE_FONT_REGULAR = new Font("SansSerif", Font.PLAIN, FONT_SIZE);
            }
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Failed to load NotoSans-Regular.ttf: " + e.getMessage());
            BASE_FONT_REGULAR = new Font("SansSerif", Font.PLAIN, FONT_SIZE);
        }

        try {
            InputStream boldStream = NametagTextureGenerator.class.getResourceAsStream("/fonts/NotoSans-Bold.ttf");
            if (boldStream != null) {
                Font loadedFont = Font.createFont(Font.TRUETYPE_FONT, boldStream);
                BASE_FONT_BOLD = loadedFont.deriveFont(Font.PLAIN, FONT_SIZE);
                boldStream.close();
            } else {
                getLogger().atWarning().log("[HyCitizens] NotoSans-Bold.ttf not found, falling back to SansSerif");
                BASE_FONT_BOLD = new Font("SansSerif", Font.BOLD, FONT_SIZE);
            }
        } catch (Exception e) {
            getLogger().atWarning().log("[HyCitizens] Failed to load NotoSans-Bold.ttf: " + e.getMessage());
            BASE_FONT_BOLD = new Font("SansSerif", Font.BOLD, FONT_SIZE);
        }
    }

    @Nullable
    public static byte[] generateTexture(@Nonnull String text) {
        return generateMultiLineTexture(Collections.singletonList(text));
    }

    @Nullable
    public static byte[] generateMultiLineTexture(@Nonnull List<String> lines) {
        if (lines.isEmpty()) {
            return null;
        }

        List<List<FormattedTextSegment>> parsedLines = new ArrayList<>();
        for (String line : lines) {
            List<FormattedTextSegment> segments = NametagFormatParser.parse(line);
            if (!segments.isEmpty()) {
                parsedLines.add(segments);
            }
        }

        if (parsedLines.isEmpty()) {
            return null;
        }

        try {
            BufferedImage measureImage = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
            Graphics2D measureG = measureImage.createGraphics();
            measureG.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            int maxLineWidth = 0;
            int lineHeight = 0;
            List<Integer> lineWidths = new ArrayList<>();

            for (List<FormattedTextSegment> lineSegments : parsedLines) {
                int lineWidth = 0;
                int lineMaxHeight = 0;

                for (FormattedTextSegment segment : lineSegments) {
                    Font font = createFont(segment);
                    measureG.setFont(font);
                    FontMetrics metrics = measureG.getFontMetrics();
                    Rectangle2D bounds = metrics.getStringBounds(segment.getText(), measureG);
                    lineWidth += (int) Math.ceil(bounds.getWidth());
                    lineMaxHeight = Math.max(lineMaxHeight, (int) Math.ceil(bounds.getHeight()));
                }

                lineWidths.add(lineWidth);
                maxLineWidth = Math.max(maxLineWidth, lineWidth);
                lineHeight = Math.max(lineHeight, lineMaxHeight);
            }

            measureG.dispose();

            int lineSpacing = (int) (lineHeight * 0.2);
            int contentWidth = maxLineWidth + PADDING * 2;
            int contentHeight = (lineHeight * parsedLines.size()) + (lineSpacing * (parsedLines.size() - 1)) + PADDING * 2;

            int imageWidth = roundUpToMultipleOf32(contentWidth);
            int imageHeight = roundUpToMultipleOf32(contentHeight);

            BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int currentY = PADDING + lineHeight - FONT_SIZE / 4;

            for (int lineIdx = 0; lineIdx < parsedLines.size(); lineIdx++) {
                List<FormattedTextSegment> lineSegments = parsedLines.get(lineIdx);
                int x = PADDING;

                for (FormattedTextSegment segment : lineSegments) {
                    Font font = createFont(segment);
                    g.setFont(font);
                    FontMetrics metrics = g.getFontMetrics();
                    String segmentText = segment.getText();
                    int segmentWidth = metrics.stringWidth(segmentText);

                    Color color = segment.getColor();
                    if (color == null) {
                        color = Color.WHITE;
                    }

                    if (segment.isGradient() && segmentWidth > 0) {
                        Color endColor = segment.getGradientEndColor();
                        LinearGradientPaint gradientPaint = new LinearGradientPaint(
                                (float) x, 0f, (float) (x + segmentWidth), 0f,
                                new float[]{0f, 1f},
                                new Color[]{color, endColor}
                        );
                        g.setPaint(gradientPaint);
                    } else {
                        g.setColor(color);
                    }

                    g.drawString(segmentText, x, currentY);

                    // Decorations use solid start color
                    g.setColor(color);

                    if (segment.isUnderline()) {
                        int underlineY = currentY + 2;
                        g.fillRect(x, underlineY, segmentWidth, 1);
                    }

                    if (segment.isStrikethrough()) {
                        int strikeY = currentY - FONT_SIZE / 3;
                        g.fillRect(x, strikeY, segmentWidth, 1);
                    }

                    x += segmentWidth;
                }

                currentY += lineHeight + lineSpacing;
            }

            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (IOException e) {
            getLogger().atWarning().log("[HyCitizens] Failed to generate nametag texture: " + e.getMessage());
            return null;
        }
    }

    @Nonnull
    private static Font createFont(@Nonnull FormattedTextSegment segment) {
        Font baseFont = segment.isBold() ? BASE_FONT_BOLD : BASE_FONT_REGULAR;

        int style = Font.PLAIN;
        if (segment.isItalic()) {
            style = Font.ITALIC;
        }

        return baseFont.deriveFont(style);
    }

    private static int roundUpToMultipleOf32(int value) {
        int result = ((value + 31) / 32) * 32;
        return Math.max(result, 32);
    }
}
