package com.example.donutbounty.core;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.OptionalLong;

/**
 * Money helpers. Amounts are stored as whole cents in a long so totals never suffer
 * floating point drift.
 */
public final class Money {
    public static final long CENTS = 100L;

    private Money() {}

    /**
     * Parses text such as "1500", "1,500", "$1,500.50", "2.5m", "$3K", "1.2B", "4t".
     * Suffixes (case-insensitive): K=thousand, M=million, B=billion, T=trillion.
     * Returns empty for anything else, for zero and for negative values.
     */
    public static OptionalLong parseCents(String text) {
        if (text == null) return OptionalLong.empty();
        String s = text.trim();
        if (s.startsWith("$")) s = s.substring(1).trim();
        if (s.isEmpty()) return OptionalLong.empty();

        BigDecimal multiplier = BigDecimal.ONE;
        char last = Character.toLowerCase(s.charAt(s.length() - 1));
        switch (last) {
            case 'k' -> multiplier = BigDecimal.valueOf(1_000L);
            case 'm' -> multiplier = BigDecimal.valueOf(1_000_000L);
            case 'b' -> multiplier = BigDecimal.valueOf(1_000_000_000L);
            case 't' -> multiplier = BigDecimal.valueOf(1_000_000_000_000L);
            default -> { }
        }
        if (multiplier != BigDecimal.ONE) s = s.substring(0, s.length() - 1);

        // digits with optional thousands separators (strictly grouped by 3) and optional decimals
        if (!s.matches("[0-9]{1,3}(,[0-9]{3})+(\\.[0-9]+)?|[0-9]+(\\.[0-9]+)?")) return OptionalLong.empty();
        s = s.replace(",", "");

        try {
            BigDecimal dollars = new BigDecimal(s).multiply(multiplier);
            BigDecimal cents = dollars.multiply(BigDecimal.valueOf(CENTS)).setScale(0, RoundingMode.HALF_UP);
            if (cents.signum() <= 0) return OptionalLong.empty();
            if (cents.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) > 0) return OptionalLong.empty();
            return OptionalLong.of(cents.longValueExact());
        } catch (ArithmeticException | NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    /** Full format, e.g. $2,500,000 or $12.50. */
    public static String format(long cents) {
        long whole = cents / CENTS;
        long rem = Math.abs(cents % CENTS);
        String w = String.format(Locale.ROOT, "%,d", whole);
        return rem == 0 ? "$" + w : String.format(Locale.ROOT, "$%s.%02d", w, rem);
    }

    /** Compact format for the HUD, e.g. $2.5M, $750K, $12.50. */
    public static String compact(long cents) {
        double dollars = cents / (double) CENTS;
        if (dollars >= 1_000_000_000_000.0) return trim(dollars / 1_000_000_000_000.0) + "T";
        if (dollars >= 1_000_000_000.0) return trim(dollars / 1_000_000_000.0) + "B";
        if (dollars >= 1_000_000.0) return trim(dollars / 1_000_000.0) + "M";
        if (dollars >= 1_000.0) return trim(dollars / 1_000.0) + "K";
        return cents % CENTS == 0
                ? "$" + (cents / CENTS)
                : String.format(Locale.ROOT, "$%.2f", dollars);
    }

    private static String trim(double v) {
        // at most 2 decimals, no trailing zeros: 2.50 -> 2.5, 3.00 -> 3
        String s = String.format(Locale.ROOT, "%.2f", v);
        if (s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return "$" + s;
    }

    /** Plain decimal dollars for CSV, e.g. 2500000.00 */
    public static String plainDollars(long cents) {
        return String.format(Locale.ROOT, "%d.%02d", cents / CENTS, Math.abs(cents % CENTS));
    }
}
