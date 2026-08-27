package com.electro.hycitizens.util;

import javax.annotation.Nonnull;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ResourceId {
    private static final Pattern SAFE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");
    private static final Set<String> WINDOWS_RESERVED = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private ResourceId() {
    }

    public static boolean isValid(String value) {
        if (value == null || !SAFE.matcher(value).matches() || value.contains("..") || value.endsWith(".")) {
            return false;
        }
        String base = value.contains(".") ? value.substring(0, value.indexOf('.')) : value;
        return !WINDOWS_RESERVED.contains(base.toUpperCase(Locale.ROOT));
    }

    @Nonnull
    public static Path resolveJson(@Nonnull Path root, String value) {
        if (!isValid(value)) {
            throw new IllegalArgumentException("Unsafe resource ID: " + value);
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path target = normalizedRoot.resolve(value + ".json").normalize();
        if (!target.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Resource path escapes its storage directory");
        }
        return target;
    }
}
