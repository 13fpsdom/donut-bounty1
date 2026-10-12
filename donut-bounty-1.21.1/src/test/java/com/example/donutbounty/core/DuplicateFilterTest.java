package com.example.donutbounty.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class DuplicateFilterTest {
    private final long[] now = {1_000};

    @Test public void sameLineInsideWindowIsDuplicate() {
        DuplicateFilter f = new DuplicateFilter(() -> now[0], 1_000);
        assertFalse(f.isDuplicate("Steve paid you $5"));
        now[0] += 500;
        assertTrue(f.isDuplicate("Steve paid you $5"));
    }

    @Test public void sameLineAfterWindowIsAccepted() {
        DuplicateFilter f = new DuplicateFilter(() -> now[0], 1_000);
        assertFalse(f.isDuplicate("Steve paid you $5"));
        now[0] += 1_000;
        assertFalse(f.isDuplicate("Steve paid you $5"));
    }

    @Test public void differentLinesAreIndependent() {
        DuplicateFilter f = new DuplicateFilter(() -> now[0], 1_000);
        assertFalse(f.isDuplicate("Steve paid you $5"));
        assertFalse(f.isDuplicate("Steve paid you $6"));
        assertFalse(f.isDuplicate("Alex paid you $5"));
        assertTrue(f.isDuplicate("Alex paid you $5"));
    }

    @Test public void zeroWindowDisablesFiltering() {
        DuplicateFilter f = new DuplicateFilter(() -> now[0], 0);
        assertFalse(f.isDuplicate("x"));
        assertFalse(f.isDuplicate("x"));
    }
}
