package com.example.notify.interfaces.rest;

import com.example.notify.application.strategy.SaveStrategy;
import com.example.notify.domain.event.UserToken;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Instant;
import java.util.Optional;

public final class StrategiesApi {

    private final SaveStrategy saveStrategy;

    public StrategiesApi(SaveStrategy saveStrategy) {
        if (saveStrategy == null) {
            throw new IllegalArgumentException("saveStrategy must not be null");
        }
        this.saveStrategy = saveStrategy;
    }

    public StrategyResponse save(SaveStrategyRequest request) {
        SaveStrategy.Result result = saveStrategy.save(command(new StrategyId(request.strategyId()), request, Optional.empty()));
        return StrategyResponse.from(result);
    }

    public StrategyResponse update(String strategyId, SaveStrategyRequest request) {
        SaveStrategy.Result result = saveStrategy.save(command(new StrategyId(strategyId), request, Optional.of(new StrategyVersion(request.expectedVersion()))));
        return StrategyResponse.from(result);
    }

    private SaveStrategy.Command command(StrategyId strategyId, SaveStrategyRequest request, Optional<StrategyVersion> version) {
        if (request == null) {
            throw new IllegalArgumentException("strategy request must not be null");
        }
        return new SaveStrategy.Command(
            strategyId,
            new StrategyName(request.name()),
            request.scope(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, request.eventType()),
            new StrategyExecutionPlan(request.executionPlan()),
            version,
            new UserToken(request.userToken()),
            new IdempotencyKey(request.idempotencyKey()),
            request.occurredAt()
        );
    }

    public record SaveStrategyRequest(
        String strategyId,
        String name,
        StrategyScope scope,
        String eventType,
        String executionPlan,
        int expectedVersion,
        String userToken,
        String idempotencyKey,
        Instant occurredAt
    ) {

    }

    public record StrategyResponse(String strategyId, int version) {

        private static StrategyResponse from(SaveStrategy.Result result) {
            return new StrategyResponse(result.strategy().id().toString(), result.strategy().version().value());
        }

    }

}
