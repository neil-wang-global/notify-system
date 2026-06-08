package com.example.notify.application.event;

import com.example.notify.application.port.CacheInvalidationPort;
import com.example.notify.domain.strategy.StrategySaved;
import org.springframework.context.event.EventListener;

public final class StrategySavedListener {

    private final CacheInvalidationPort cacheInvalidation;

    public StrategySavedListener(CacheInvalidationPort cacheInvalidation) {
        if (cacheInvalidation == null) { throw new IllegalArgumentException("cacheInvalidation must not be null"); }
        this.cacheInvalidation = cacheInvalidation;
    }

    @EventListener
    public void onStrategySaved(StrategySaved event) {
        cacheInvalidation.invalidate(event.strategyId(), event.version());
    }

}
