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
    void degradeSetsDegradedWithReason() {
        DegradationState state = new DegradationState();

        state.degrade("Redis unavailable");

        assertThat(state.isDegraded()).isTrue();
        assertThat(state.reason()).isEqualTo("Redis unavailable");
    }

    @Test
    void recoverClearsDegradedState() {
        DegradationState state = new DegradationState();
        state.degrade("Redis unavailable");

        state.recover();

        assertThat(state.isDegraded()).isFalse();
        assertThat(state.reason()).isEmpty();
    }

    @Test
    void degradeOverwritesPreviousReason() {
        DegradationState state = new DegradationState();
        state.degrade("Redis unavailable");

        state.degrade("Kafka unavailable");

        assertThat(state.isDegraded()).isTrue();
        assertThat(state.reason()).isEqualTo("Kafka unavailable");
    }

}
