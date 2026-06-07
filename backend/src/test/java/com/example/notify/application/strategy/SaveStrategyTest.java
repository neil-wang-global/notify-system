package com.example.notify.application.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.notify.domain.event.User;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.event.UserToken;
import com.example.notify.domain.event.Users;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleConnector;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategySaved;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SaveStrategyTest {

    @Test
    void savesNewStrategyAndEmitsEvent() {
        InMemoryStrategies strategies = new InMemoryStrategies();
        SaveStrategy saveStrategy = new SaveStrategy(strategies, new FixedUsers());

        SaveStrategy.Result result = saveStrategy.save(new SaveStrategy.Command(
            new StrategyId("strategy-1"),
            new StrategyName("Product view alert"),
            StrategyScope.global(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan("plan-1"),
            Optional.empty(),
            new UserToken("token-1"),
            new IdempotencyKey("request-1"),
            Instant.parse("2026-06-07T00:00:00Z")
        ));

        assertEquals(new StrategyVersion(1), result.strategy().version());
        assertEquals(List.of(new StrategySaved(new StrategyId("strategy-1"), new StrategyVersion(1), Instant.parse("2026-06-07T00:00:00Z"))), result.events());
        assertEquals(new UserId("user-1"), result.actor().userId());
    }

    @Test
    void idempotencyKeyPreventsDuplicateSubmit() {
        InMemoryStrategies strategies = new InMemoryStrategies();
        SaveStrategy saveStrategy = new SaveStrategy(strategies, new FixedUsers());
        SaveStrategy.Command command = command(Optional.empty(), new IdempotencyKey("request-1"));

        SaveStrategy.Result first = saveStrategy.save(command);
        SaveStrategy.Result duplicate = saveStrategy.save(command);

        assertEquals(first.strategy(), duplicate.strategy());
        assertEquals(List.of(), duplicate.events());
        assertEquals(1, strategies.saved.size());
    }

    @Test
    void staleVersionFails() {
        InMemoryStrategies strategies = new InMemoryStrategies();
        SaveStrategy saveStrategy = new SaveStrategy(strategies, new FixedUsers());
        saveStrategy.save(command(Optional.empty(), new IdempotencyKey("request-1")));

        SaveStrategy.Command staleCommand = command(Optional.of(new StrategyVersion(1)), new IdempotencyKey("request-2"));
        saveStrategy.save(staleCommand);

        SaveStrategy.Command repeatedOldVersion = command(Optional.of(new StrategyVersion(1)), new IdempotencyKey("request-3"));
        assertThrows(IllegalArgumentException.class, () -> saveStrategy.save(repeatedOldVersion));
    }

    @Test
    void idempotencyKeyWithDifferentPayloadFails() {
        InMemoryStrategies strategies = new InMemoryStrategies();
        SaveStrategy saveStrategy = new SaveStrategy(strategies, new FixedUsers());
        saveStrategy.save(command(Optional.empty(), new IdempotencyKey("request-1")));

        SaveStrategy.Command changedPayload = new SaveStrategy.Command(
            new StrategyId("strategy-1"),
            new StrategyName("Changed alert"),
            StrategyScope.users(new UserId("user-1")),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan("plan-1"),
            Optional.empty(),
            new UserToken("token-1"),
            new IdempotencyKey("request-1"),
            Instant.parse("2026-06-07T00:00:00Z")
        );

        assertThrows(IllegalArgumentException.class, () -> saveStrategy.save(changedPayload));
    }

    @Test
    void rejectsNullCommand() {
        SaveStrategy saveStrategy = new SaveStrategy(new InMemoryStrategies(), new FixedUsers());

        assertThrows(IllegalArgumentException.class, () -> saveStrategy.save(null));
    }

    private static SaveStrategy.Command command(Optional<StrategyVersion> expectedVersion, IdempotencyKey idempotencyKey) {
        return new SaveStrategy.Command(
            new StrategyId("strategy-1"),
            new StrategyName("Product view alert"),
            StrategyScope.users(new UserId("user-1")),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan("plan-1"),
            expectedVersion,
            new UserToken("token-1"),
            idempotencyKey,
            Instant.parse("2026-06-07T00:00:00Z")
        );
    }

    private static final class InMemoryStrategies implements Strategies {
        private final List<Strategy> saved = new ArrayList<>();
        private final Map<StrategyId, Strategy> strategies = new HashMap<>();
        private final Map<IdempotencyKey, Strategy> idempotencyKeys = new HashMap<>();
        private final Map<IdempotencyKey, String> fingerprints = new HashMap<>();

        @Override
        public Optional<Strategy> find(StrategyId strategyId) {
            return Optional.ofNullable(strategies.get(strategyId));
        }

        @Override
        public Optional<Strategy> findByIdempotencyKey(IdempotencyKey idempotencyKey) {
            return Optional.ofNullable(idempotencyKeys.get(idempotencyKey));
        }

        @Override
        public Optional<String> fingerprint(IdempotencyKey idempotencyKey) {
            return Optional.ofNullable(fingerprints.get(idempotencyKey));
        }

        @Override
        public void save(Strategy strategy, IdempotencyKey idempotencyKey, String fingerprint) {
            strategies.put(strategy.id(), strategy);
            idempotencyKeys.put(idempotencyKey, strategy);
            fingerprints.put(idempotencyKey, fingerprint);
            saved.add(strategy);
        }
    }

    private static final class FixedUsers implements Users {
        @Override
        public User resolve(UserToken userToken) {
            return new User(new UserId("user-1"), List.of(new UserGroupId("group-1")));
        }
    }

}
