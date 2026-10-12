package com.example.donutbounty.core;

import java.util.function.LongSupplier;

/** Countdown with start / pause / resume / stop. The clock is injected so it can be tested. */
public final class BountyTimer {
    public enum State { IDLE, RUNNING, PAUSED, FINISHED }

    private final LongSupplier clockMillis;
    private State state = State.IDLE;
    private long durationMillis;
    private long remainingMillis;
    private long resumedAt;

    public BountyTimer(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /** Starts a fresh countdown. Only allowed from IDLE or FINISHED. */
    public synchronized boolean start(long durationMillis) {
        refresh();
        if (durationMillis <= 0 || (state != State.IDLE && state != State.FINISHED)) return false;
        this.durationMillis = durationMillis;
        this.remainingMillis = durationMillis;
        this.resumedAt = clockMillis.getAsLong();
        this.state = State.RUNNING;
        return true;
    }

    public synchronized boolean pause() {
        refresh();
        if (state != State.RUNNING) return false;
        remainingMillis -= clockMillis.getAsLong() - resumedAt;
        state = State.PAUSED;
        return true;
    }

    public synchronized boolean resume() {
        refresh();
        if (state != State.PAUSED) return false;
        resumedAt = clockMillis.getAsLong();
        state = State.RUNNING;
        return true;
    }

    /** Stops and clears the bounty (back to IDLE) from any state. */
    public synchronized boolean stop() {
        refresh();
        if (state == State.IDLE) return false;
        state = State.IDLE;
        remainingMillis = 0;
        durationMillis = 0;
        return true;
    }

    public synchronized State state() {
        refresh();
        return state;
    }

    public synchronized long remainingMillis() {
        refresh();
        if (state == State.RUNNING) return Math.max(0, remainingMillis - (clockMillis.getAsLong() - resumedAt));
        return Math.max(0, remainingMillis);
    }

    public synchronized long durationMillis() {
        return durationMillis;
    }

    /** 1.0 at the start, 0.0 at the end. */
    public synchronized double fractionRemaining() {
        long d = durationMillis;
        return d <= 0 ? 0 : Math.min(1.0, remainingMillis() / (double) d);
    }

    private void refresh() {
        if (state == State.RUNNING && clockMillis.getAsLong() - resumedAt >= remainingMillis) {
            remainingMillis = 0;
            state = State.FINISHED;
        }
    }
}
