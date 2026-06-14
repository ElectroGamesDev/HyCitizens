package com.electro.hycitizens.nametag;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.awt.Color;

public class FormattedTextSegment {
    private final String text;
    private final Color color;
    private final boolean bold;
    private final boolean italic;
    private final boolean underline;
    private final boolean strikethrough;

    public FormattedTextSegment(@Nonnull String text, @Nullable Color color, boolean bold, boolean italic, boolean underline, boolean strikethrough) {
        this.text = text;
        this.color = color;
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
