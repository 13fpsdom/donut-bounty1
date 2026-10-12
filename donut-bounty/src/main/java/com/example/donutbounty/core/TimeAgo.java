package com.example.donutbounty.core;

/** Short relative times for the leaderboard ("just now", "5m ago", "3h ago", "2d ago"). */
public final class TimeAgo {
    private TimeAgo() {}

    public static String format(long thenMillis, long nowMillis) {
        long s = (nowMillis - thenMillis) / 1000;
        if (s < 5) return "just now";
        if (s < 60) return s + "s ago";
        long m = s / 60;
        if (m < 60) return m + "m ago";
        long h = m / 60;
        if (h < 24) return h + "h ago";
        return (h / 24) + "d ago";
    }
}
