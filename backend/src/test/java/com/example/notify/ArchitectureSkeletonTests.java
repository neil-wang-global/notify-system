package com.example.notify;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

class ArchitectureSkeletonTests {

	private static final String[] REQUIRED_PACKAGE_INFOS = {
		"com.example.notify.interfaces.rest.package-info",
		"com.example.notify.interfaces.kafka.package-info",
		"com.example.notify.application.strategy.package-info",
		"com.example.notify.application.event.package-info",
		"com.example.notify.application.notification.package-info",
		"com.example.notify.application.exception.package-info",
		"com.example.notify.domain.strategy.package-info",
		"com.example.notify.domain.event.package-info",
		"com.example.notify.domain.notification.package-info",
		"com.example.notify.domain.exception.package-info",
		"com.example.notify.engine.matching.package-info",
		"com.example.notify.engine.timebox.package-info",
		"com.example.notify.engine.idempotency.package-info",
		"com.example.notify.infrastructure.persistence.package-info",
		"com.example.notify.infrastructure.persistence.entity.package-info",
		"com.example.notify.infrastructure.cache.package-info",
		"com.example.notify.infrastructure.redis.package-info",
		"com.example.notify.infrastructure.kafka.package-info",
		"com.example.notify.config.package-info"
	};

	@Test
	void requiredPackageSkeletonExists() {
		for (String requiredPackageInfo : REQUIRED_PACKAGE_INFOS) {
			assertDoesNotThrow(() -> Class.forName(requiredPackageInfo), requiredPackageInfo);
		}
	}

}
