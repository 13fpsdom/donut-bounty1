package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TimeAgoTest {
    @Test public void buckets() {
        long now = 10_000_000_000L;
        assertEquals("just now", TimeAgo.format(now, now));
        assertEquals("just now", TimeAgo.format(now + 5_000, now));      // clock skew into the future
        assertEquals("30s ago", TimeAgo.format(now - 30_000, now));
        assertEquals("5m ago", TimeAgo.format(now - 5 * 60_000L, now));
        assertEquals("3h ago", TimeAgo.format(now - 3 * 3_600_000L, now));
        assertEquals("2d ago", TimeAgo.format(now - 2 * 86_400_000L, now));
    }
}
