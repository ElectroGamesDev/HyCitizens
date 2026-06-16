package com.electro.hycitizens.nametag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NametagFormatParser {
    private static final Map<String, Color> NAMED_COLORS = Map.ofEntries(
            Map.entry("BLACK", Color.decode("#000000")),
            Map.entry("WHITE", Color.decode("#FFFFFF")),
            Map.entry("RED", Color.decode("#FF0000")),
            Map.entry("GREEN", Color.decode("#00FF00")),
            Map.entry("BLUE", Color.decode("#0000FF")),
            Map.entry("YELLOW", Color.decode("#FFFF00")),
            Map.entry("ORANGE", Color.decode("#FFA500")),
            Map.entry("PINK", Color.decode("#FFC0CB")),
            Map.entry("PURPLE", Color.decode("#800080")),
            Map.entry("CYAN", Color.decode("#00FFFF")),
            Map.entry("MAGENTA", Color.decode("#FF00FF")),
            Map.entry("LIME", Color.decode("#00FF00")),
            Map.entry("MAROON", Color.decode("#800000")),
            Map.entry("NAVY", Color.decode("#000080")),
            Map.entry("TEAL", Color.decode("#008080")),
            Map.entry("OLIVE", Color.decode("#808000")),
            Map.entry("SILVER", Color.decode("#C0C0C0")),
            Map.entry("GRAY", Color.decode("#808080")),
            Map.entry("GREY", Color.decode("#808080")),
            Map.entry("BROWN", Color.decode("#A52A2A")),
            Map.entry("GOLD", Color.decode("#FFD700")),
            Map.entry("ORCHID", Color.decode("#DA70D6")),
            Map.entry("SALMON", Color.decode("#FA8072")),
            Map.entry("TURQUOISE", Color.decode("#40E0D0")),
            Map.entry("VIOLET", Color.decode("#EE82EE")),
            Map.entry("INDIGO", Color.decode("#4B0082")),
            Map.entry("CORAL", Color.decode("#FF7F50")),
            Map.entry("CRIMSON", Color.decode("#DC143C")),
            Map.entry("KHAKI", Color.decode("#F0E68C")),
            Map.entry("PLUM", Color.decode("#DDA0DD")),
            Map.entry("CHOCOLATE", Color.decode("#D2691E")),
            Map.entry("TAN", Color.decode("#D2B48C")),
            Map.entry("LIGHTBLUE", Color.decode("#ADD8E6")),
            Map.entry("LIGHTGREEN", Color.decode("#90EE90")),
            Map.entry("LIGHTGRAY", Color.decode("#D3D3D3")),
            Map.entry("LIGHTGREY", Color.decode("#D3D3D3")),
            Map.entry("DARKRED", Color.decode("#8B0000")),
            Map.entry("DARKGREEN", Color.decode("#006400")),
            Map.entry("DARKBLUE", Color.decode("#00008B")),
            Map.entry("DARKGRAY", Color.decode("#A9A9A9")),
            Map.entry("DARKGREY", Color.decode("#A9A9A9")),
            Map.entry("LIGHTPINK", Color.decode("#FFB6C1")),
            Map.entry("LIGHTYELLOW", Color.decode("#FFFFE0")),
            Map.entry("LIGHTCYAN", Color.decode("#E0FFFF")),
            Map.entry("LIGHTMAGENTA", Color.decode("#FF77FF")),
            Map.entry("ORANGERED", Color.decode("#FF4500")),
            Map.entry("DEEPSKYBLUE", Color.decode("#00BFFF"))
    );

    private static final Pattern FORMAT_PATTERN = Pattern.compile("\\{([^}]+)}");

    public static boolean hasFormatCodes(@Nonnull String text) {
        return FORMAT_PATTERN.matcher(text).find();
    }

    @Nonnull
    public static List<FormattedTextSegment> parse(@Nonnull String text) {
        List<FormattedTextSegment> segments = new ArrayList<>();
        Color currentColor = Color.WHITE;
        boolean bold = false;
        boolean italic = false;
        boolean underline = false;
        boolean strikethrough = false;

        // Inline gradient state
        Color gradientStartColor = null;
        StringBuilder pendingGradientText = new StringBuilder();

        // Shorthand gradient state
        Color[] shorthandGradientColors = null;

        Matcher matcher = FORMAT_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            // Flush text before this format code
            if (matcher.start() > lastEnd) {
                String plainText = text.substring(lastEnd, matcher.start());
                if (!plainText.isEmpty()) {
                    if (shorthandGradientColors != null) {
                        addShorthandGradientSegments(segments, plainText, shorthandGradientColors,
                                bold, italic, underline, strikethrough);
                        shorthandGradientColors = null;
                    } else if (gradientStartColor != null) {
                        pendingGradientText.append(plainText);
                    } else {
                        segments.add(new FormattedTextSegment(plainText, currentColor, bold, italic, underline, strikethrough));
                    }
                }
            }

            String code = matcher.group(1);
            String upperCode = code.toUpperCase();

            if (upperCode.equals("RESET")) {
                flushPendingGradient(segments, pendingGradientText, gradientStartColor, null,
                        bold, italic, underline, strikethrough);
                gradientStartColor = null;
                pendingGradientText.setLength(0);
                shorthandGradientColors = null;
                currentColor = Color.WHITE;
                bold = false;
                italic = false;
                underline = false;
                strikethrough = false;
            } else if (upperCode.equals("BOLD")) {
                bold = true;
            } else if (upperCode.equals("ITALIC")) {
                italic = true;
            } else if (upperCode.equals("UNDERLINE")) {
                underline = true;
            } else if (upperCode.equals("STRIKETHROUGH")) {
                strikethrough = true;
            } else if (isInlineGradientMarker(upperCode)) {
                // Contains a dash (but not only dashes) -- inline gradient marker
                boolean leadingDash = upperCode.startsWith("-");
                boolean trailingDash = upperCode.endsWith("-");

                if (leadingDash && trailingDash) {
                    // {-COLOR-} : midpoint -- ends current gradient, starts new one
                    String colorPart = upperCode.substring(1, upperCode.length() - 1).trim();
                    Color midColor = resolveColor(colorPart);
                    if (midColor != null) {
                        flushPendingGradient(segments, pendingGradientText, gradientStartColor, midColor,
                                bold, italic, underline, strikethrough);
                        pendingGradientText.setLength(0);
                        gradientStartColor = midColor;
                    }
                } else if (leadingDash) {
                    // {-COLOR} : gradient end
                    String colorPart = upperCode.substring(1).trim();
                    Color endColor = resolveColor(colorPart);
                    if (endColor != null) {
                        flushPendingGradient(segments, pendingGradientText, gradientStartColor, endColor,
                                bold, italic, underline, strikethrough);
                        pendingGradientText.setLength(0);
                        gradientStartColor = null;
                    }
                } else if (trailingDash) {
                    // {COLOR-} : gradient start
                    String colorPart = upperCode.substring(0, upperCode.length() - 1).trim();
                    Color startColor = resolveColor(colorPart);
                    if (startColor != null) {
                        flushPendingGradient(segments, pendingGradientText, gradientStartColor, null,
                                bold, italic, underline, strikethrough);
                        pendingGradientText.setLength(0);
                        gradientStartColor = startColor;
                    }
                }
            } else {
                // Try shorthand gradient: {color1-color2-...}
                Color[] shorthandColors = tryParseShorthandGradient(upperCode);
                if (shorthandColors != null) {
                    flushPendingGradient(segments, pendingGradientText, gradientStartColor, null,
                            bold, italic, underline, strikethrough);
                    pendingGradientText.setLength(0);
                    gradientStartColor = null;
                    shorthandGradientColors = shorthandColors;
                } else if (upperCode.startsWith("#") && upperCode.length() == 7) {
                    // Hex color
                    try {
                        currentColor = Color.decode(upperCode);
                    } catch (Exception ignored) {
                    }
                } else {
                    // Named color
                    Color namedColor = NAMED_COLORS.get(upperCode);
                    if (namedColor != null) {
                        currentColor = namedColor;
                    }
                }
            }

            lastEnd = matcher.end();
        }

        // Flush remaining text
        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            if (!remaining.isEmpty()) {
                if (shorthandGradientColors != null) {
                    addShorthandGradientSegments(segments, remaining, shorthandGradientColors,
                            bold, italic, underline, strikethrough);
                } else if (gradientStartColor != null) {
                    pendingGradientText.append(remaining);
                } else {
                    segments.add(new FormattedTextSegment(remaining, currentColor, bold, italic, underline, strikethrough));
                }
            }
        }

        // Flush any unfinished gradient at end of string
        flushPendingGradient(segments, pendingGradientText, gradientStartColor, null,
                bold, italic, underline, strikethrough);

        return segments.isEmpty()
                ? List.of(new FormattedTextSegment(text, Color.WHITE, false, false, false, false))
                : segments;
    }

    private static boolean isInlineGradientMarker(@Nonnull String upperCode) {
        if (upperCode.length() < 2) return false;
        boolean hasDash = upperCode.indexOf('-') >= 0;
        if (!hasDash) return false;
        // Exclude codes that are only dashes
        String withoutDashes = upperCode.replace("-", "");
        return !withoutDashes.isEmpty();
    }

    private static void flushPendingGradient(@Nonnull List<FormattedTextSegment> segments,
                                             @Nonnull StringBuilder pendingText,
                                             @Nullable Color startColor, @Nullable Color endColor,
                                             boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        if (pendingText.length() == 0) return;
        String text = pendingText.toString();
        if (startColor != null && endColor != null) {
            segments.add(new FormattedTextSegment(text, startColor, endColor, bold, italic, underline, strikethrough));
        } else {
            segments.add(new FormattedTextSegment(text, startColor != null ? startColor : Color.WHITE,
                    bold, italic, underline, strikethrough));
        }
    }

    @Nullable
    private static Color[] tryParseShorthandGradient(@Nonnull String upperCode) {
        int dashIdx = upperCode.indexOf('-');
        if (dashIdx < 0) return null;

        String[] parts = upperCode.split("-");
        if (parts.length < 2) return null;

        Color[] colors = new Color[parts.length];
        for (int i = 0; i < parts.length; i++) {
            colors[i] = resolveColor(parts[i]);
            if (colors[i] == null) return null;
        }
        return colors;
    }

    private static void addShorthandGradientSegments(@Nonnull List<FormattedTextSegment> segments,
                                                     @Nonnull String text,
                                                     @Nonnull Color[] colors,
                                                     boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        if (colors.length < 2 || text.isEmpty()) {
            segments.add(new FormattedTextSegment(text,
                    colors.length > 0 ? colors[0] : Color.WHITE,
                    bold, italic, underline, strikethrough));
            return;
        }

        int numTransitions = colors.length - 1;
        int len = text.length();

        StringBuilder segText = new StringBuilder();
        int currentSegIdx = 0;

        for (int i = 0; i < len; i++) {
            int segIdx = Math.min((i * numTransitions) / len, numTransitions - 1);

            if (segIdx != currentSegIdx && segText.length() > 0) {
                segments.add(new FormattedTextSegment(segText.toString(),
                        colors[currentSegIdx], colors[currentSegIdx + 1],
                        bold, italic, underline, strikethrough));
                segText.setLength(0);
            }

            currentSegIdx = segIdx;
            segText.append(text.charAt(i));
        }

        if (segText.length() > 0) {
            segments.add(new FormattedTextSegment(segText.toString(),
                    colors[currentSegIdx], colors[currentSegIdx + 1],
                    bold, italic, underline, strikethrough));
        }
    }

    @Nullable
    private static Color resolveColor(@Nonnull String part) {
        if (part.isEmpty()) return null;
        if (part.startsWith("#") && part.length() == 7) {
            try {
                return Color.decode(part);
            } catch (Exception e) {
                return null;
            }
        }
        return NAMED_COLORS.get(part);
    }

    @Nonnull
    public static String stripFormatCodes(@Nonnull String text) {
        return FORMAT_PATTERN.matcher(text).replaceAll("");
    }

    @Nonnull
    public static String normalizeForHashing(@Nonnull String text) {
        return text.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
