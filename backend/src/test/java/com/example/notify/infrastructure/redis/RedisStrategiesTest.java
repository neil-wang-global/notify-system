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
import java.util.List;
import org.junit.jupiter.api.Test;

class RedisStrategiesTest {

    private static final Duration W = Duration.ofSeconds(30);
    private static final Duration S = Duration.ofSeconds(10);
    private static final Duration D = Duration.ZERO;
    private static final List<String> DEDUP = List.of("customerId", "userId", "eventType");

    @Test
    void versionGuardRejectsStaleWritesAndStoresPlanAndIndexes() {
        RedisStrategies redisStrategies = new RedisStrategies();
        StrategyId strategyId = new StrategyId("strategy-1");
        Strategy strategy = new Strategy(strategyId, new StrategyName("test"),
            StrategyScope.users(new UserId("user-1")),
            new RuleAst.Group(RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
                new RuleAst.Comparison("productId", RuleOperator.EQ, "P001")
            )),
            new StrategyExecutionPlan(W, S, D, DEDUP), 1, new StrategyVersion(2));

        assertTrue(redisStrategies.refresh(strategy));
        Strategy stale = new Strategy(strategyId, new StrategyName("test"),
            StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan(W, S, D, DEDUP), 1, new StrategyVersion(1));
        assertFalse(redisStrategies.refresh(stale));

        assertEquals(new StrategyExecutionPlan(W, S, D, DEDUP), redisStrategies.plan(strategyId).orElseThrow());
        assertTrue(redisStrategies.scopeIndex().userStrategies(new UserId("user-1")).contains(strategyId));
        assertTrue(redisStrategies.eventTypeIndex().strategies(new EventType("PRODUCT_VIEW")).contains(strategyId));
        assertTrue(redisStrategies.fieldIndex().strategies("productId", "P001").contains(strategyId));
    }

    @Test
    void storesGroupScopeIndexes() {
        RedisStrategies redisStrategies = new RedisStrategies();
        StrategyId strategyId = new StrategyId("strategy-1");

        assertTrue(redisStrategies.refresh(new Strategy(strategyId, new StrategyName("test"),
            StrategyScope.userGroups(new UserGroupId("group-1")),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan(W, S, D, DEDUP), 1, new StrategyVersion(1))));

        assertTrue(redisStrategies.scopeIndex().groupStrategies(new UserGroupId("group-1")).contains(strategyId));
    }

    @Test
    void acceptedRefreshRemovesOldIndexes() {
        RedisStrategies redisStrategies = new RedisStrategies();
        StrategyId strategyId = new StrategyId("strategy-1");
        redisStrategies.refresh(new Strategy(strategyId, new StrategyName("test"),
            StrategyScope.users(new UserId("user-1")),
            new RuleAst.Group(RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
                new RuleAst.Comparison("productId", RuleOperator.EQ, "P001")
            )),
            new StrategyExecutionPlan(W, S, D, DEDUP), 1, new StrategyVersion(1)));

        assertTrue(redisStrategies.refresh(new Strategy(strategyId, new StrategyName("test"),
            StrategyScope.users(new UserId("user-2")),
            new RuleAst.Group(RuleConnector.AND, List.of(
                new RuleAst.Comparison("eventType", RuleOperator.EQ, "ORDER_CREATED"),
                new RuleAst.Comparison("orderId", RuleOperator.EQ, "O001")
            )),
            new StrategyExecutionPlan(W, S, D, DEDUP), 1, new StrategyVersion(2))));

        assertFalse(redisStrategies.scopeIndex().userStrategies(new UserId("user-1")).contains(strategyId));
        assertFalse(redisStrategies.eventTypeIndex().strategies(new EventType("PRODUCT_VIEW")).contains(strategyId));
        assertFalse(redisStrategies.fieldIndex().strategies("productId", "P001").contains(strategyId));
        assertTrue(redisStrategies.scopeIndex().userStrategies(new UserId("user-2")).contains(strategyId));
        assertTrue(redisStrategies.eventTypeIndex().strategies(new EventType("ORDER_CREATED")).contains(strategyId));
        assertTrue(redisStrategies.fieldIndex().strategies("orderId", "O001").contains(strategyId));
    }

}
