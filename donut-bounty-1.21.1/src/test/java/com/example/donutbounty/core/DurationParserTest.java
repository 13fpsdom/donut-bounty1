package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class DurationParserTest {
    private static long ms(String s) {
        return DurationParser.parseMillis(s).orElseThrow(() -> new AssertionError("should parse: " + s));
    }

    @Test public void accepted() {
        assertEquals(1_800_000L, ms("30"));          // bare number = minutes
        assertEquals(2_700_000L, ms("45m"));
        assertEquals(90_000L, ms("90s"));
        assertEquals(5_400_000L, ms("1h30m"));
        assertEquals(5_400_000L, ms("1h 30m"));
        assertEquals(5_415_000L, ms("1h30m15s"));
        assertEquals(5_400_000L, ms("1:30:00"));
        assertEquals(600_000L, ms("10:00"));
        assertEquals(604_800_000L, ms("7d"));
        assertEquals(1_800_000L, ms("  30  "));
    }

    @Test public void rejected() {
        for (String s : new String[]{null, "", "  ", "0", "0s", "abc", "5x", "8d", "1:75", "1:30:75", "-5", "m", "h"}) {
            assertFalse("should reject '" + s + "'", DurationParser.parseMillis(s).isPresent());
        }
    }

    @Test public void formatting() {
        assertEquals("1:02:05", DurationParser.format(3_725_000));
        assertEquals("01:05", DurationParser.format(65_000));
        assertEquals("00:00", DurationParser.format(0));
        assertEquals("00:01", DurationParser.format(1));
    }
}
