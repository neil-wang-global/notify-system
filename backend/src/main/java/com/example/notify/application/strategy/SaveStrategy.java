package com.example.notify.application.strategy;

import com.example.notify.domain.event.User;
import com.example.notify.domain.event.UserToken;
import com.example.notify.domain.event.Users;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategySaved;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class SaveStrategy {

    private final Strategies strategies;
    private final Users users;

    public SaveStrategy(Strategies strategies, Users users) {
        if (strategies == null) {
            throw new IllegalArgumentException("strategies must not be null");
        }
        if (users == null) {
            throw new IllegalArgumentException("users must not be null");
        }
        this.strategies = strategies;
        this.users = users;
    }

    public Result save(Command command) {
        if (command == null) {
            throw new IllegalArgumentException("strategy save command must not be null");
        }
        command.validate();
        String fingerprint = command.fingerprint();
        User actor = users.resolve(command.userToken());
        Optional<Strategy> duplicate = strategies.findByIdempotencyKey(command.idempotencyKey());
        if (duplicate.isPresent()) {
            if (!strategies.fingerprint(command.idempotencyKey()).orElseThrow().equals(fingerprint)) {
                throw new IllegalArgumentException("idempotency key was used with a different request");
            }
            return new Result(duplicate.get(), actor, List.of());
        }

        Strategy strategy = nextStrategy(command);
        strategies.save(strategy, command.idempotencyKey(), fingerprint);
        return new Result(strategy, actor, List.of(new StrategySaved(strategy.id(), strategy.version(), command.occurredAt())));
    }

    private Strategy nextStrategy(Command command) {
        Optional<Strategy> existing = strategies.find(command.strategyId());
        if (existing.isEmpty()) {
            if (command.expectedVersion().isPresent()) {
                throw new IllegalArgumentException("new strategy must not include expected version");
            }
            return Strategy.create(command.strategyId(), command.name(), command.scope(), command.ruleAst(), command.executionPlan(), command.threshold());
        }

        Strategy current = existing.get();
        if (command.expectedVersion().isEmpty() || !current.version().equals(command.expectedVersion().get())) {
            throw new IllegalArgumentException("strategy version conflict");
        }
        return current.update(command.name(), command.scope(), command.ruleAst(), command.executionPlan(), command.threshold());
    }

    public record Command(
        StrategyId strategyId,
        StrategyName name,
        StrategyScope scope,
        RuleAst ruleAst,
        StrategyExecutionPlan executionPlan,
        int threshold,
        Optional<StrategyVersion> expectedVersion,
        UserToken userToken,
        IdempotencyKey idempotencyKey,
        Instant occurredAt
    ) {

        public void validate() {
            if (strategyId == null || name == null || scope == null || ruleAst == null || executionPlan == null) {
                throw new IllegalArgumentException("strategy save command is incomplete");
            }
            if (expectedVersion == null || userToken == null || idempotencyKey == null || occurredAt == null) {
                throw new IllegalArgumentException("strategy save command metadata is incomplete");
            }
            if (threshold < 1) {
                throw new IllegalArgumentException("threshold must be at least 1");
            }
        }

        private String fingerprint() {
            return strategyId + "|" + name + "|" + scope + "|" + ruleAst + "|" + executionPlan + "|" + threshold + "|" + expectedVersion;
        }

    }

    public record Result(Strategy strategy, User actor, List<StrategySaved> events) {

        public Result {
            if (strategy == null || actor == null || events == null) {
                throw new IllegalArgumentException("save strategy result is incomplete");
            }
            events = List.copyOf(events);
        }

    }

}
