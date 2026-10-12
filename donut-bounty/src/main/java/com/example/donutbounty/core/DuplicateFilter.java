package com.example.donutbounty.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Drops an identical message that arrives again within a short window. This protects against the
 * same line being delivered twice (for example through two event channels or a client hiccup).
 *
 * Trade-off: if the exact same payment line really is sent twice inside the window, the second one is
 * dropped. The default window is short (1 second) to keep that rare; set it to 0 to disable.
 */
public final class DuplicateFilter {
    private final LongSupplier clockMillis;
    private final long windowMillis;
    private final Map<String, Long> lastSeen = new HashMap<>();

    public DuplicateFilter(LongSupplier clockMillis, long windowMillis) {
        this.clockMillis = clockMillis;
        this.windowMillis = Math.max(0, windowMillis);
    }

    /** Returns true if this key was already accepted within the window. Otherwise records it and returns false. */
    public synchronized boolean isDuplicate(String key) {
        if (windowMillis == 0) return false;
        long now = clockMillis.getAsLong();
        prune(now);
        Long prev = lastSeen.get(key);
        if (prev != null && now - prev < windowMillis) return true;
        lastSeen.put(key, now);
        return false;
    }

    private void prune(long now) {
        Iterator<Map.Entry<String, Long>> it = lastSeen.entrySet().iterator();
        while (it.hasNext()) {
            if (now - it.next().getValue() >= windowMillis) it.remove();
        }
    }
}
