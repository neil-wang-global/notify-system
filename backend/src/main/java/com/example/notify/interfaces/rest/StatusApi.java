package com.example.notify.interfaces.rest;

import com.example.notify.config.DegradationState;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public final class StatusApi {

    private final Supplier<String> kafkaStatus;
    private final Supplier<String> redisStatus;
    private final Supplier<String> strategyCacheVersion;
    private final DegradationState degradationState;

    public StatusApi(Supplier<String> kafkaStatus,
                     Supplier<String> redisStatus,
                     Supplier<String> strategyCacheVersion,
                     DegradationState degradationState) {
        if (kafkaStatus == null) {
            throw new IllegalArgumentException("kafkaStatus must not be null");
        }
        if (redisStatus == null) {
            throw new IllegalArgumentException("redisStatus must not be null");
        }
        if (strategyCacheVersion == null) {
            throw new IllegalArgumentException("strategyCacheVersion must not be null");
        }
        if (degradationState == null) {
            throw new IllegalArgumentException("degradationState must not be null");
        }
        this.kafkaStatus = kafkaStatus;
        this.redisStatus = redisStatus;
        this.strategyCacheVersion = strategyCacheVersion;
        this.degradationState = degradationState;
    }

    @GetMapping
    public StatusResponse current() {
        return new StatusResponse(
            kafkaStatus.get(),
            redisStatus.get(),
            strategyCacheVersion.get(),
            degradationState.isDegraded() ? "DEGRADED: " + degradationState.reason() : "NONE"
        );
    }

    public record StatusResponse(String kafka, String redis, String strategyCacheVersion, String degradationStatus) {

        public static StatusResponse healthy() {
            return new StatusResponse("DISABLED", "DISABLED", "unknown", "NONE");
        }

    }

}
