package com.example.notify.config;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks whether the system is in degraded mode.
 * Thread-safe via a single AtomicReference holding an immutable state object
 * containing both the degraded flag and reason (T-04).
 * Includes hysteresis: requires N consecutive failures to degrade and
 * N consecutive successes to recover, preventing rapid toggling (T-33).
 */
public class DegradationState {

    private static final int DEFAULT_THRESHOLD = 3;

    private final int failureThreshold;
    private final int recoveryThreshold;
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);
    private final AtomicInteger consecutiveSuccesses = new AtomicInteger(0);
    private final AtomicReference<State> state = new AtomicReference<>(new State(false, ""));

    public DegradationState() {
        this(DEFAULT_THRESHOLD, DEFAULT_THRESHOLD);
    }

    public DegradationState(int failureThreshold, int recoveryThreshold) {
        if (failureThreshold < 1) { throw new IllegalArgumentException("failureThreshold must be at least 1"); }
        if (recoveryThreshold < 1) { throw new IllegalArgumentException("recoveryThreshold must be at least 1"); }
        this.failureThreshold = failureThreshold;
        this.recoveryThreshold = recoveryThreshold;
    }

    public void degrade(String reason) {
        if (reason == null) { reason = ""; }
        consecutiveSuccesses.set(0);
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureThreshold) {
            state.set(new State(true, reason));
        }
    }

    public void recover() {
        consecutiveFailures.set(0);
        int successes = consecutiveSuccesses.incrementAndGet();
        if (successes >= recoveryThreshold) {
            state.set(new State(false, ""));
        }
    }

    public boolean isDegraded() {
        return state.get().degraded;
    }

    public String reason() {
        return state.get().reason;
    }

    private record State(boolean degraded, String reason) {}

}
