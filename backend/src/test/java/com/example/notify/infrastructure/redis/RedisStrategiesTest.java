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
import java.util.List;
import org.junit.jupiter.api.Test;

class RedisStrategiesTest {

    @Test
    void versionGuardRejectsStaleWritesAndStoresPlanAndIndexes() {
        RedisStrategies redisStrategies = new RedisStrategies();
        StrategyId strategyId = new StrategyId("strategy-1");
        RedisStrategy strategy = new RedisStrategy(
            strategyId,
            new StrategyVersion(2),
            new StrategyExecutionPlan("plan-v2"),
            StrategyScope.users(new UserId("user-1")),
            new EventType("PRODUCT_VIEW"),
            List.of(new RedisFieldIndex("productId", "P001"))
        );

        assertTrue(redisStrategies.refresh(strategy));
        assertFalse(redisStrategies.refresh(new RedisStrategy(
            strategyId,
            new StrategyVersion(1),
            new StrategyExecutionPlan("plan-v1"),
            StrategyScope.global(),
            new EventType("PRODUCT_VIEW"),
            List.of()
        )));

        assertEquals(new StrategyExecutionPlan("plan-v2"), redisStrategies.plan(strategyId).orElseThrow());
        assertTrue(redisStrategies.scopeIndex().userStrategies(new UserId("user-1")).contains(strategyId));
        assertTrue(redisStrategies.eventTypeIndex().strategies(new EventType("PRODUCT_VIEW")).contains(strategyId));
        assertTrue(redisStrategies.fieldIndex().strategies("productId", "P001").contains(strategyId));
    }

    @Test
    void storesGroupScopeIndexes() {
        RedisStrategies redisStrategies = new RedisStrategies();
        StrategyId strategyId = new StrategyId("strategy-1");

        assertTrue(redisStrategies.refresh(new RedisStrategy(
            strategyId,
            new StrategyVersion(1),
            new StrategyExecutionPlan("plan-v1"),
            StrategyScope.userGroups(new UserGroupId("group-1")),
            new EventType("PRODUCT_VIEW"),
            List.of()
        )));

        assertTrue(redisStrategies.scopeIndex().groupStrategies(new UserGroupId("group-1")).contains(strategyId));
    }

    @Test
    void acceptedRefreshRemovesOldIndexes() {
        RedisStrategies redisStrategies = new RedisStrategies();
        StrategyId strategyId = new StrategyId("strategy-1");
        redisStrategies.refresh(new RedisStrategy(
            strategyId,
            new StrategyVersion(1),
            new StrategyExecutionPlan("plan-v1"),
            StrategyScope.users(new UserId("user-1")),
            new EventType("PRODUCT_VIEW"),
            List.of(new RedisFieldIndex("productId", "P001"))
        ));

        assertTrue(redisStrategies.refresh(new RedisStrategy(
            strategyId,
            new StrategyVersion(2),
            new StrategyExecutionPlan("plan-v2"),
            StrategyScope.users(new UserId("user-2")),
            new EventType("ORDER_CREATED"),
            List.of(new RedisFieldIndex("orderId", "O001"))
        )));

        assertFalse(redisStrategies.scopeIndex().userStrategies(new UserId("user-1")).contains(strategyId));
        assertFalse(redisStrategies.eventTypeIndex().strategies(new EventType("PRODUCT_VIEW")).contains(strategyId));
        assertFalse(redisStrategies.fieldIndex().strategies("productId", "P001").contains(strategyId));
        assertTrue(redisStrategies.scopeIndex().userStrategies(new UserId("user-2")).contains(strategyId));
        assertTrue(redisStrategies.eventTypeIndex().strategies(new EventType("ORDER_CREATED")).contains(strategyId));
        assertTrue(redisStrategies.fieldIndex().strategies("orderId", "O001").contains(strategyId));
    }

}
