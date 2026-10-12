package com.example.donutbounty.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Running totals of confirmed payments per player. Usernames are matched case-insensitively. */
public final class Leaderboard {

    public record Entry(String name, long totalCents, int count, long lastPaymentMillis, long lastAmountCents) {}

    private static final Comparator<Entry> RANKING = Comparator
            .comparingLong(Entry::totalCents).reversed()
            .thenComparing(Comparator.comparingInt(Entry::count).reversed())
            .thenComparing(e -> e.name().toLowerCase(Locale.ROOT));

    private final Map<String, Entry> byKey = new HashMap<>();

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT);
    }

    public synchronized void record(String player, long amountCents, long whenMillis) {
        if (player == null || amountCents <= 0) throw new IllegalArgumentException("player and a positive amount are required");
        String k = key(player);
        Entry old = byKey.get(k);
        if (old == null) {
            byKey.put(k, new Entry(player, amountCents, 1, whenMillis, amountCents));
        } else {
            boolean newer = whenMillis >= old.lastPaymentMillis();
            byKey.put(k, new Entry(
                    newer ? player : old.name(), // keep the capitalisation from the most recent payment
                    Math.addExact(old.totalCents(), amountCents),
                    old.count() + 1,
                    newer ? whenMillis : old.lastPaymentMillis(),
                    newer ? amountCents : old.lastAmountCents()));
        }
    }

    /** Highest total first; ties broken by more payments, then name. */
    public synchronized List<Entry> ranked() {
        List<Entry> list = new ArrayList<>(byKey.values());
        list.sort(RANKING);
        return list;
    }

    public synchronized Optional<Entry> get(String player) {
        return Optional.ofNullable(byKey.get(key(player)));
    }

    public synchronized long totalCents() {
        long t = 0;
        for (Entry e : byKey.values()) t += e.totalCents();
        return t;
    }

    public synchronized int paymentCount() {
        int c = 0;
        for (Entry e : byKey.values()) c += e.count();
        return c;
    }

    public synchronized int playerCount() {
        return byKey.size();
    }

    public synchronized void clear() {
        byKey.clear();
    }

    /** Replaces all data (used when loading from disk). */
    public synchronized void replaceAll(Collection<Entry> entries) {
        byKey.clear();
        for (Entry e : entries) byKey.put(key(e.name()), e);
    }
}
