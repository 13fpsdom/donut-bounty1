package com.example.donutbounty.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BountyTimerTest {
    private final long[] now = {0};
    private final BountyTimer t = new BountyTimer(() -> now[0]);

    @Test public void fullLifecycle() {
        assertEquals(BountyTimer.State.IDLE, t.state());
        assertTrue(t.start(60_000));
        now[0] += 10_000;
        assertEquals(50_000, t.remainingMillis());

        assertTrue(t.pause());
        now[0] += 100_000;                       // paused time must not count
        assertEquals(BountyTimer.State.PAUSED, t.state());
        assertEquals(50_000, t.remainingMillis());

        assertTrue(t.resume());
        now[0] += 49_999;
        assertEquals(BountyTimer.State.RUNNING, t.state());
        assertEquals(1, t.remainingMillis());
        now[0] += 1;
        assertEquals(BountyTimer.State.FINISHED, t.state());
        assertEquals(0, t.remainingMillis());
    }

    @Test public void invalidTransitionsAreRefused() {
        assertFalse(t.pause());
        assertFalse(t.resume());
        assertFalse(t.stop());
        assertFalse(t.start(0));
        assertTrue(t.start(1_000));
        assertFalse("cannot start while running", t.start(5_000));
        assertFalse("cannot resume while running", t.resume());
        assertTrue(t.pause());
        assertFalse("cannot pause twice", t.pause());
        assertFalse("cannot start while paused", t.start(5_000));
    }

    @Test public void stopResetsToIdleFromAnyState() {
        t.start(10_000);
        assertTrue(t.stop());
        assertEquals(BountyTimer.State.IDLE, t.state());

        t.start(10_000);
        t.pause();
        assertTrue(t.stop());
        assertEquals(BountyTimer.State.IDLE, t.state());

        t.start(10_000);
        now[0] += 20_000;
        assertEquals(BountyTimer.State.FINISHED, t.state());
        assertTrue(t.stop());
        assertEquals(BountyTimer.State.IDLE, t.state());
    }

    @Test public void canStartAgainAfterFinishing() {
        t.start(1_000);
        now[0] += 5_000;
        assertTrue(t.start(2_000));
        assertEquals(2_000, t.remainingMillis());
    }

    @Test public void fractionRemaining() {
        t.start(100_000);
        now[0] += 25_000;
        assertEquals(0.75, t.fractionRemaining(), 0.0001);
    }
}
