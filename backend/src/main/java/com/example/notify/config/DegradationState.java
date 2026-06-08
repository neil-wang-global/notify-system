package com.example.notify.config;

/**
 * Simple bean that tracks whether the system is in degraded mode.
 * Thread-safe via volatile fields.
 */
public class DegradationState {

    private volatile boolean degraded = false;
    private volatile String reason = "";

    public void degrade(String reason) {
        this.degraded = true;
        this.reason = reason;
    }

    public void recover() {
        this.degraded = false;
        this.reason = "";
    }

    public boolean isDegraded() {
        return degraded;
    }

    public String reason() {
        return reason;
    }

}
