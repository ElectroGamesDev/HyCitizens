package com.electro.hycitizens.nametag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;

public class FormattedTextSegment {
    private final String text;
    private final Color color;
    private final Color gradientEndColor;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final boolean strikethrough;

    public FormattedTextSegment(@Nonnull String text, @Nullable Color color, boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        this(text, color, null, bold, italic, underline, strikethrough);
    }

    public FormattedTextSegment(@Nonnull String text, @Nullable Color color, @Nullable Color gradientEndColor,
                                boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        this.text = text;
        this.color = color;
        this.gradientEndColor = gradientEndColor;
        this.bold = bold;
        this.italic = italic;
        this.underline = underline;
        this.strikethrough = strikethrough;
    }

    @Nonnull
    public String getText() {
        return text;
    }

    @Nullable
    public Color getColor() {
        return color;
    }

    @Nullable
    public Color getGradientEndColor() {
        return gradientEndColor;
    }

    public boolean isGradient() {
        return gradientEndColor != null;
    }

    public boolean isBold() {
        return bold;
    }

    public boolean isItalic() {
        return italic;
    }

    public boolean isUnderline() {
        return underline;
    }

    public boolean isStrikethrough() {
        return strikethrough;
    }
}
