package com.example.notify.interfaces.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/status")
public final class StatusApi {

    private final StatusResponse status;

    public StatusApi(StatusResponse status) {
        if (status == null) {
            throw new IllegalArgumentException("status must not be null");
        }
        this.status = status;
    }

    @GetMapping
    public StatusResponse current() {
        return status;
    }

    public record StatusResponse(String kafka, String redis, String strategyCacheVersion, String degradationStatus) {

        public static StatusResponse healthy() {
            return new StatusResponse("RUNNING", "HEALTHY", "unknown", "NONE");
        }

    }

}
