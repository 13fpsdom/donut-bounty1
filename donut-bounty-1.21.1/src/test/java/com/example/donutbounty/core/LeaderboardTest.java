package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class LeaderboardTest {

    @Test public void totalsCountsAndLastPayment() {
        Leaderboard lb = new Leaderboard();
        lb.record("Steve", 1_000, 100);
        lb.record("Steve", 2_500, 300);
        lb.record("Steve", 500, 200);   // older than the latest, must not replace "last"
        Leaderboard.Entry e = lb.get("steve").orElseThrow();
        assertEquals(4_000, e.totalCents());
        assertEquals(3, e.count());
        assertEquals(300, e.lastPaymentMillis());
        assertEquals(2_500, e.lastAmountCents());
    }

    @Test public void usernamesMergeCaseInsensitively() {
        Leaderboard lb = new Leaderboard();
        lb.record("Steve", 100, 1);
        lb.record("STEVE", 100, 2);
        assertEquals(1, lb.playerCount());
        assertEquals(200, lb.get("steve").orElseThrow().totalCents());
        assertEquals("STEVE", lb.get("Steve").orElseThrow().name());
    }

    @Test public void rankedByTotalThenCountThenName() {
        Leaderboard lb = new Leaderboard();
        lb.record("Low", 100, 1);
        lb.record("High", 9_000, 1);
        lb.record("MidMany", 250, 1);
        lb.record("MidMany", 250, 2);
        lb.record("MidOne", 500, 1);
        lb.record("alpha", 100, 1);
        List<Leaderboard.Entry> r = lb.ranked();
        assertEquals("High", r.get(0).name());
        assertEquals("MidMany", r.get(1).name());   // 500 total, 2 payments beats 1 payment
        assertEquals("MidOne", r.get(2).name());
        assertEquals("alpha", r.get(3).name());     // 100 total tie with Low: name order, case-insensitive
        assertEquals("Low", r.get(4).name());
    }

    @Test public void aggregateTotals() {
        Leaderboard lb = new Leaderboard();
        lb.record("A", 100, 1);
        lb.record("B", 200, 1);
        lb.record("A", 300, 2);
        assertEquals(600, lb.totalCents());
        assertEquals(3, lb.paymentCount());
        assertEquals(2, lb.playerCount());
    }

    @Test public void clearEmptiesEverything() {
        Leaderboard lb = new Leaderboard();
        lb.record("A", 100, 1);
        lb.clear();
        assertTrue(lb.ranked().isEmpty());
        assertEquals(0, lb.totalCents());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonPositiveAmounts() {
        new Leaderboard().record("A", 0, 1);
    }

    @Test(expected = ArithmeticException.class)
    public void overflowIsNotSilentlyWrapped() {
        Leaderboard lb = new Leaderboard();
        lb.record("A", Long.MAX_VALUE, 1);
        lb.record("A", 1, 2);
    }
}
