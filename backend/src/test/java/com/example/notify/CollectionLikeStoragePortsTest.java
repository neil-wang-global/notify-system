package com.example.notify;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CollectionLikeStoragePortsTest {

    private static final String[] DOMAIN_PORTS = {
        "com.example.notify.domain.strategy.Strategies",
        "com.example.notify.domain.exception.UserOperationExceptions",
        "com.example.notify.domain.exception.NotificationExceptions",
        "com.example.notify.domain.event.Users"
    };

    private static final String[] INFRASTRUCTURE_TYPES = {
        "com.example.notify.infrastructure.persistence.JdbcStrategies",
        "com.example.notify.infrastructure.cache.CacheStrategies"
    };

    @Test
    void domainPortsUseCollectionLikeNames() {
        for (String domainPort : DOMAIN_PORTS) {
            Class<?> type = assertDoesNotThrow(() -> Class.forName(domainPort), domainPort);

            assertTrue(type.isInterface(), domainPort + " must be a domain port interface");
            assertFalse(type.getSimpleName().endsWith("Repository"), domainPort + " must not use repository naming");
        }
    }

    @Test
    void infrastructureUsesStorageSpecificNames() {
        for (String infrastructureType : INFRASTRUCTURE_TYPES) {
            Class<?> type = assertDoesNotThrow(() -> Class.forName(infrastructureType), infrastructureType);

            assertFalse(type.getSimpleName().endsWith("Repository"), infrastructureType + " must not use repository naming");
        }

        Class<?> jdbcStrategies = assertDoesNotThrow(() -> Class.forName("com.example.notify.infrastructure.persistence.JdbcStrategies"));
        Class<?> cacheStrategies = assertDoesNotThrow(() -> Class.forName("com.example.notify.infrastructure.cache.CacheStrategies"));

        assertTrue(Modifier.isFinal(jdbcStrategies.getModifiers()));
        assertTrue(Modifier.isFinal(cacheStrategies.getModifiers()));
    }

    @Test
    void domainDoesNotContainRepositoryNamedTypes() throws Exception {
        Path domainRoot = Path.of("src/main/java/com/example/notify/domain");

        try (var files = Files.walk(domainRoot)) {
            assertTrue(files
                .filter(Files::isRegularFile)
                .map(Path::getFileName)
                .map(Path::toString)
                .noneMatch(fileName -> fileName.contains("Repository")));
        }
    }

}
