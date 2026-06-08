package com.example.notify.infrastructure.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=",
        "spring.datasource.url=jdbc:h2:mem:notify-redis-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.sql.init.mode=never"
})
@Testcontainers
@Tag("docker")
class RealRedisStrategiesIT {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    private StringRedisTemplate redisTemplate;

    private RealRedisStrategies realRedisStrategies;

    @BeforeEach
    void setUp() {
        // Flush all keys before each test
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        realRedisStrategies = new RealRedisStrategies(redisTemplate);
    }

    @Test
    void versionGuardRejectsStaleWrites() {
        StrategyId strategyId = new StrategyId("strategy-1");
        RedisStrategy v2 = new RedisStrategy(
                strategyId,
                new StrategyVersion(2),
                new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId")),
                StrategyScope.users(new UserId("user-1")),
                new EventType("PRODUCT_VIEW"),
                List.of(new RedisFieldIndex("productId", "P001"))
        );
        assertTrue(realRedisStrategies.refresh(v2));

        RedisStrategy v1 = new RedisStrategy(
                strategyId,
                new StrategyVersion(1),
                new StrategyExecutionPlan(Duration.ofSeconds(60), Duration.ofSeconds(20), Duration.ZERO, List.of("customerId")),
                StrategyScope.global(),
                new EventType("ORDER_CREATED"),
                List.of()
        );
        assertFalse(realRedisStrategies.refresh(v1));
    }

    @Test
    void storesAndLoadsExecutionPlan() {
        StrategyId strategyId = new StrategyId("strategy-plan-test");
        StrategyExecutionPlan plan = new StrategyExecutionPlan(
                Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType"));
        RedisStrategy strategy = new RedisStrategy(
                strategyId, new StrategyVersion(1), plan,
                StrategyScope.global(), new EventType("PRODUCT_VIEW"), List.of()
        );

        assertTrue(realRedisStrategies.refresh(strategy));
        StrategyExecutionPlan loaded = realRedisStrategies.plan(strategyId).orElseThrow();
        assertEquals(plan, loaded);
    }

    @Test
    void refreshRemovesOldIndexesAndAddsNewOnes() {
        StrategyId strategyId = new StrategyId("strategy-refresh-test");

        RedisStrategy v1 = new RedisStrategy(
                strategyId, new StrategyVersion(1),
                new StrategyExecutionPlan("plan-v1"),
                StrategyScope.users(new UserId("user-1")),
                new EventType("PRODUCT_VIEW"),
                List.of(new RedisFieldIndex("productId", "P001"))
        );
        assertTrue(realRedisStrategies.refresh(v1));

        RedisStrategy v2 = new RedisStrategy(
                strategyId, new StrategyVersion(2),
                new StrategyExecutionPlan("plan-v2"),
                StrategyScope.users(new UserId("user-2")),
                new EventType("ORDER_CREATED"),
                List.of(new RedisFieldIndex("orderId", "O001"))
        );
        assertTrue(realRedisStrategies.refresh(v2));

        // Old indexes should be gone, new ones present
        Set<StrategyId> candidates = realRedisStrategies.candidates(
                "cust-1", "user-2", Set.of(), "ORDER_CREATED", Map.of("orderId", "O001"));
        assertTrue(candidates.contains(strategyId));

        // user-1 + PRODUCT_VIEW should NOT contain this strategy anymore
        Set<StrategyId> oldCandidates = realRedisStrategies.candidates(
                "cust-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of("productId", "P001"));
        assertFalse(oldCandidates.contains(strategyId));
    }

    @Test
    void candidateLookupReturnsMatchingStrategies() {
        StrategyId s1 = new StrategyId("s-global");
        StrategyId s2 = new StrategyId("s-user");

        realRedisStrategies.refresh(new RedisStrategy(
                s1, new StrategyVersion(1),
                new StrategyExecutionPlan("plan-1"),
                StrategyScope.global(), new EventType("PRODUCT_VIEW"), List.of()
        ));
        realRedisStrategies.refresh(new RedisStrategy(
                s2, new StrategyVersion(1),
                new StrategyExecutionPlan("plan-2"),
                StrategyScope.users(new UserId("user-1")),
                new EventType("PRODUCT_VIEW"), List.of()
        ));

        Set<StrategyId> candidates = realRedisStrategies.candidates(
                "cust-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of());
        assertTrue(candidates.contains(s1));
        assertTrue(candidates.contains(s2));
    }

    @Test
    void candidateLookupFiltersByFieldIndex() {
        StrategyId s1 = new StrategyId("s-field-match");
        StrategyId s2 = new StrategyId("s-field-nomatch");

        realRedisStrategies.refresh(new RedisStrategy(
                s1, new StrategyVersion(1),
                new StrategyExecutionPlan("plan-1"),
                StrategyScope.global(), new EventType("PRODUCT_VIEW"),
                List.of(new RedisFieldIndex("category", "ELECTRONICS"))
        ));
        realRedisStrategies.refresh(new RedisStrategy(
                s2, new StrategyVersion(1),
                new StrategyExecutionPlan("plan-2"),
                StrategyScope.global(), new EventType("PRODUCT_VIEW"),
                List.of(new RedisFieldIndex("category", "BOOKS"))
        ));

        Set<StrategyId> candidates = realRedisStrategies.candidates(
                "cust-1", "user-1", Set.of(), "PRODUCT_VIEW",
                Map.of("category", "ELECTRONICS"));
        assertTrue(candidates.contains(s1));
        assertFalse(candidates.contains(s2));
    }

    @Test
    void candidateLookupIncludesGroupScope() {
        StrategyId s1 = new StrategyId("s-group");

        realRedisStrategies.refresh(new RedisStrategy(
                s1, new StrategyVersion(1),
                new StrategyExecutionPlan("plan-1"),
                StrategyScope.userGroups(new UserGroupId("group-1")),
                new EventType("PRODUCT_VIEW"), List.of()
        ));

        Set<StrategyId> candidates = realRedisStrategies.candidates(
                "cust-1", "user-1", Set.of("group-1"), "PRODUCT_VIEW", Map.of());
        assertTrue(candidates.contains(s1));
    }

    @Test
    void planReturnsEmptyForUnknownStrategy() {
        assertTrue(realRedisStrategies.plan(new StrategyId("nonexistent")).isEmpty());
    }
}
