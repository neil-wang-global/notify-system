package com.example.notify.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DegradationStateTest {

    @Test
    void initialStateIsNotDegraded() {
        DegradationState state = new DegradationState();

        assertThat(state.isDegraded()).isFalse();
        assertThat(state.reason()).isEmpty();
    }

    @Test
    void degradeSetsDegradedWithReasonAfterThreshold() {
        DegradationState state = new DegradationState(2, 2);

        state.degrade("Redis unavailable");
        assertThat(state.isDegraded()).isFalse();

        state.degrade("Redis unavailable");
        assertThat(state.isDegraded()).isTrue();
        assertThat(state.reason()).isEqualTo("Redis unavailable");
    }

    @Test
    void recoverClearsDegradedStateAfterThreshold() {
        DegradationState state = new DegradationState(1, 2);
        state.degrade("Redis unavailable");
        assertThat(state.isDegraded()).isTrue();

        state.recover();
        assertThat(state.isDegraded()).isTrue();

        state.recover();
        assertThat(state.isDegraded()).isFalse();
        assertThat(state.reason()).isEmpty();
    }

    @Test
    void degradeOverwritesPreviousReason() {
        DegradationState state = new DegradationState(1, 1);
        state.degrade("Redis unavailable");

        state.degrade("Kafka unavailable");

        assertThat(state.isDegraded()).isTrue();
        assertThat(state.reason()).isEqualTo("Kafka unavailable");
    }

    @Test
    void singleThresholdDegradesImmediately() {
        DegradationState state = new DegradationState(1, 1);
        state.degrade("Redis unavailable");
        assertThat(state.isDegraded()).isTrue();
        assertThat(state.reason()).isEqualTo("Redis unavailable");
    }

    @Test
    void singleThresholdRecoversImmediately() {
        DegradationState state = new DegradationState(1, 1);
        state.degrade("Redis unavailable");
        state.recover();
        assertThat(state.isDegraded()).isFalse();
        assertThat(state.reason()).isEmpty();
    }

    @Test
    void successResetsFailureCounter() {
        DegradationState state = new DegradationState(3, 1);

        state.degrade("Redis unavailable");
        state.degrade("Redis unavailable");
        state.recover(); // resets failure counter
        state.degrade("Redis unavailable"); // failure count = 1 again

        assertThat(state.isDegraded()).isFalse();
    }

    @Test
    void failureResetsSuccessCounter() {
        DegradationState state = new DegradationState(1, 3);
        state.degrade("Redis unavailable");

        state.recover();
        state.recover();
        state.degrade("Redis unavailable"); // resets success counter
        state.recover();

        assertThat(state.isDegraded()).isTrue(); // still degraded, success count only 1
    }

}
