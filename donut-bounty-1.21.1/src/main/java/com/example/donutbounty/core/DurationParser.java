package com.example.donutbounty.core;

import java.util.OptionalLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses bounty durations into milliseconds.
 *
 * Accepted: "30" (a bare number means MINUTES), "45m", "90s", "2h", "1h30m", "1h 30m 15s", "1:30:00" (h:mm:ss), "10:00" (m:ss).
 * Limits: at least 1 second, at most 7 days.
 */
public final class DurationParser {
    public static final long MIN_MILLIS = 1_000L;
    public static final long MAX_MILLIS = 7L * 24 * 60 * 60 * 1000;

    private static final Pattern UNITS = Pattern.compile("^(?:(\\d+)\\s*d)?\\s*(?:(\\d+)\\s*h)?\\s*(?:(\\d+)\\s*m)?\\s*(?:(\\d+)\\s*s)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLON = Pattern.compile("^(\\d{1,3}):(\\d{2})(?::(\\d{2}))?$");
    private static final Pattern BARE = Pattern.compile("^\\d{1,6}$");

    private DurationParser() {}

    public static OptionalLong parseMillis(String text) {
        if (text == null) return OptionalLong.empty();
        String s = text.trim();
        if (s.isEmpty()) return OptionalLong.empty();

        long total;
        if (BARE.matcher(s).matches()) {
            total = Long.parseLong(s) * 60_000L;
        } else {
            Matcher c = COLON.matcher(s);
            if (c.matches()) {
                long a = Long.parseLong(c.group(1));
                long b = Long.parseLong(c.group(2));
                if (b > 59) return OptionalLong.empty();
                if (c.group(3) != null) {
                    long sec = Long.parseLong(c.group(3));
                    if (sec > 59) return OptionalLong.empty();
                    total = (a * 3600 + b * 60 + sec) * 1000L;
                } else {
                    total = (a * 60 + b) * 1000L;
                }
            } else {
                Matcher u = UNITS.matcher(s);
                if (!u.matches()) return OptionalLong.empty();
                if (u.group(1) == null && u.group(2) == null && u.group(3) == null && u.group(4) == null) {
                    return OptionalLong.empty();
                }
                try {
                    long d = u.group(1) == null ? 0 : Long.parseLong(u.group(1));
                    long h = u.group(2) == null ? 0 : Long.parseLong(u.group(2));
                    long m = u.group(3) == null ? 0 : Long.parseLong(u.group(3));
                    long sec = u.group(4) == null ? 0 : Long.parseLong(u.group(4));
                    total = (((d * 24 + h) * 60 + m) * 60 + sec) * 1000L;
                } catch (NumberFormatException e) {
                    return OptionalLong.empty();
                }
            }
        }
        if (total < MIN_MILLIS || total > MAX_MILLIS) return OptionalLong.empty();
        return OptionalLong.of(total);
    }

    /** 3725000 -> "1:02:05", 65000 -> "01:05". */
    public static String format(long millis) {
        long totalSec = Math.max(0, (millis + 999) / 1000); // round up so 0:00 only shows at the true end
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }
}
