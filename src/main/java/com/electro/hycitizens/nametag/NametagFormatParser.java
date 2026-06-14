package com.electro.hycitizens.nametag;

import javax.annotation.Nonnull;
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

        Matcher matcher = FORMAT_PATTERN.matcher(text);
        int lastEnd = 0;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String plainText = text.substring(lastEnd, matcher.start());
                if (!plainText.isEmpty()) {
                    segments.add(new FormattedTextSegment(plainText, currentColor, bold, italic, underline, strikethrough));
                }
            }

            String code = matcher.group(1).toUpperCase();

            if (code.equals("RESET")) {
                currentColor = Color.WHITE;
                bold = false;
                italic = false;
                underline = false;
                strikethrough = false;
            } else if (code.equals("BOLD")) {
                bold = true;
            } else if (code.equals("ITALIC")) {
                italic = true;
            } else if (code.equals("UNDERLINE")) {
                underline = true;
            } else if (code.equals("STRIKETHROUGH")) {
                strikethrough = true;
            } else if (code.startsWith("#") && code.length() == 7) {
                try {
                    currentColor = Color.decode(code);
                } catch (Exception ignored) {
                }
            } else {
                Color namedColor = NAMED_COLORS.get(code);
                if (namedColor != null) {
                    currentColor = namedColor;
                }
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String remaining = text.substring(lastEnd);
            if (!remaining.isEmpty()) {
                segments.add(new FormattedTextSegment(remaining, currentColor, bold, italic, underline, strikethrough));
            }
        }

        return segments.isEmpty() ? List.of(new FormattedTextSegment(text, Color.WHITE, false, false, false, false)) : segments;
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
