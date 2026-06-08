package com.example.notify.infrastructure.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleConnector;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Duration;
import java.util.ArrayList;
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

    private static final Duration W = Duration.ofSeconds(30);
    private static final Duration S = Duration.ofSeconds(10);
    private static final Duration D = Duration.ZERO;

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
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        realRedisStrategies = new RealRedisStrategies(redisTemplate);
    }

    @Test
    void versionGuardRejectsStaleWrites() {
        StrategyId strategyId = new StrategyId("strategy-1");
        Strategy v2 = makeStrategy(strategyId, 2, StrategyScope.users(new UserId("user-1")),
            new RuleAst.Group(RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
                new RuleAst.Comparison("productId", RuleOperator.EQ, "P001"))));
        assertTrue(realRedisStrategies.refresh(v2));

        Strategy v1 = makeStrategy(strategyId, 1, StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "ORDER_CREATED"));
        assertFalse(realRedisStrategies.refresh(v1));
    }

    @Test
    void storesAndLoadsExecutionPlan() {
        StrategyId strategyId = new StrategyId("strategy-plan-test");
        Strategy strategy = makeStrategy(strategyId, 1, StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"));

        assertTrue(realRedisStrategies.refresh(strategy));
        StrategyExecutionPlan loaded = realRedisStrategies.plan(strategyId).orElseThrow();
        assertEquals(strategy.executionPlan(), loaded);
    }

    @Test
    void refreshRemovesOldIndexesAndAddsNewOnes() {
        StrategyId strategyId = new StrategyId("strategy-refresh-test");

        Strategy v1 = makeStrategy(strategyId, 1, StrategyScope.users(new UserId("user-1")),
            new RuleAst.Group(RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
                new RuleAst.Comparison("productId", RuleOperator.EQ, "P001"))));
        assertTrue(realRedisStrategies.refresh(v1));

        Strategy v2 = makeStrategy(strategyId, 2, StrategyScope.users(new UserId("user-2")),
            new RuleAst.Group(RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "ORDER_CREATED"),
                new RuleAst.Comparison("orderId", RuleOperator.EQ, "O001"))));
        assertTrue(realRedisStrategies.refresh(v2));

        Set<StrategyId> candidates = realRedisStrategies.candidates(
                "cust-1", "user-2", Set.of(), "ORDER_CREATED", Map.of("orderId", "O001"));
        assertTrue(candidates.contains(strategyId));

        Set<StrategyId> oldCandidates = realRedisStrategies.candidates(
                "cust-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of("productId", "P001"));
        assertFalse(oldCandidates.contains(strategyId));
    }

    @Test
    void candidateLookupReturnsMatchingStrategies() {
        StrategyId s1 = new StrategyId("s-global");
        StrategyId s2 = new StrategyId("s-user");

        realRedisStrategies.refresh(makeStrategy(s1, 1, StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW")));
        realRedisStrategies.refresh(makeStrategy(s2, 1, StrategyScope.users(new UserId("user-1")),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW")));

        Set<StrategyId> candidates = realRedisStrategies.candidates(
                "cust-1", "user-1", Set.of(), "PRODUCT_VIEW", Map.of());
        assertTrue(candidates.contains(s1));
        assertTrue(candidates.contains(s2));
    }

    @Test
    void candidateLookupFiltersByFieldIndex() {
        StrategyId s1 = new StrategyId("s-field-match");
        StrategyId s2 = new StrategyId("s-field-nomatch");

        realRedisStrategies.refresh(makeStrategy(s1, 1, StrategyScope.global(),
            new RuleAst.Group(RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
                new RuleAst.Comparison("category", RuleOperator.EQ, "ELECTRONICS")))));
        realRedisStrategies.refresh(makeStrategy(s2, 1, StrategyScope.global(),
            new RuleAst.Group(RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
                new RuleAst.Comparison("category", RuleOperator.EQ, "BOOKS")))));

        Set<StrategyId> candidates = realRedisStrategies.candidates(
                "cust-1", "user-1", Set.of(), "PRODUCT_VIEW",
                Map.of("category", "ELECTRONICS"));
        assertTrue(candidates.contains(s1));
        assertFalse(candidates.contains(s2));
    }

    @Test
    void candidateLookupIncludesGroupScope() {
        StrategyId s1 = new StrategyId("s-group");

        realRedisStrategies.refresh(makeStrategy(s1, 1,
            StrategyScope.userGroups(new UserGroupId("group-1")),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW")));

        Set<StrategyId> candidates = realRedisStrategies.candidates(
                "cust-1", "user-1", Set.of("group-1"), "PRODUCT_VIEW", Map.of());
        assertTrue(candidates.contains(s1));
    }

    @Test
    void planReturnsEmptyForUnknownStrategy() {
        assertTrue(realRedisStrategies.plan(new StrategyId("nonexistent")).isEmpty());
    }

    private Strategy makeStrategy(StrategyId id, int version, StrategyScope scope, RuleAst ast) {
        return new Strategy(id, new StrategyName("test"), scope, ast,
            new StrategyExecutionPlan(W, S, D, List.of("customerId", "userId", "eventType")),
            1, new StrategyVersion(version));
    }
}
