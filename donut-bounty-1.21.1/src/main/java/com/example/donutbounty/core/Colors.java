package com.example.donutbounty.core;

import java.util.OptionalInt;

/** Parses "#RRGGBB" or "#AARRGGBB" (the # is optional) into an ARGB int. */
public final class Colors {
    private Colors() {}

    public static OptionalInt parse(String text) {
        if (text == null) return OptionalInt.empty();
        String s = text.trim();
        if (s.startsWith("#")) s = s.substring(1);
        if (!s.matches("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}")) return OptionalInt.empty();
        long v = Long.parseLong(s, 16);
        if (s.length() == 6) v |= 0xFF000000L; // opaque when no alpha is given
        return OptionalInt.of((int) v);
    }

    public static int parseOr(String text, int fallback) {
        OptionalInt v = parse(text);
        return v.isPresent() ? v.getAsInt() : fallback;
    }

    public static String toHex(int argb) {
        return String.format("#%08X", argb);
    }
}
