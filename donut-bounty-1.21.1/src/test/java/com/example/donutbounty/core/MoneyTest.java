package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.OptionalLong;
import org.junit.Test;

public class MoneyTest {
    private static long cents(String s) {
        OptionalLong v = Money.parseCents(s);
        assertTrue("expected '" + s + "' to parse", v.isPresent());
        return v.getAsLong();
    }

    @Test public void plainAndGrouped() {
        assertEquals(150_000L, cents("1500"));
        assertEquals(150_000L, cents("1,500"));
        assertEquals(150_000L, cents("$1,500"));
        assertEquals(1_250L, cents("$12.50"));
    }

    @Test public void suffixes() {
        assertEquals(250_000_000L, cents("$2.5m"));
        assertEquals(250_000_000L, cents("2.5M"));
        assertEquals(300_000L, cents("3k"));
        assertEquals(120_000_000_000L, cents("1.2B"));
        assertEquals(100_000_000_000_000L, cents("1T"));
    }

    @Test public void rejectsBadInput() {
        for (String s : new String[]{null, "", " ", "$", "abc", "0", "$0", "-5", "$-5", "1,2,3", "12,34", "1..5", "5x", "m", "1e5", "$5 m", "1,500,", "0.001"}) {
            assertFalse("should reject '" + s + "'", Money.parseCents(s).isPresent());
        }
    }

    @Test public void rejectsOverflow() {
        assertFalse(Money.parseCents("99999999999999999999").isPresent());
    }

    @Test public void formatting() {
        assertEquals("$1,500", Money.format(150_000));
        assertEquals("$12.50", Money.format(1_250));
        assertEquals("$2.5M", Money.compact(250_000_000L));
        assertEquals("$750K", Money.compact(75_000_000L));
        assertEquals("$3B", Money.compact(300_000_000_000L));
        assertEquals("$12.50", Money.compact(1_250));
        assertEquals("$5", Money.compact(500));
        assertEquals("2500000.00", Money.plainDollars(250_000_000L));
        assertEquals("0.05", Money.plainDollars(5));
    }
}
