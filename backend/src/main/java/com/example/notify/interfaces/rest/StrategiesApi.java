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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strategies")
public final class StrategiesApi {

    private final SaveStrategy saveStrategy;

    public StrategiesApi(SaveStrategy saveStrategy) {
        if (saveStrategy == null) {
            throw new IllegalArgumentException("saveStrategy must not be null");
        }
        this.saveStrategy = saveStrategy;
    }

    @PostMapping
    public StrategyResponse save(@RequestBody SaveStrategyRequest request) {
        validate(request);
        SaveStrategy.Result result = saveStrategy.save(command(new StrategyId(request.strategyId()), request, Optional.empty()));
        return StrategyResponse.from(result);
    }

    @PutMapping("/{strategyId}")
    public StrategyResponse update(@PathVariable String strategyId, @RequestBody SaveStrategyRequest request) {
        validate(request);
        if (!strategyId.equals(request.strategyId())) {
            throw new IllegalArgumentException("path strategyId must match request strategyId");
        }
        SaveStrategy.Result result = saveStrategy.save(command(new StrategyId(strategyId), request, Optional.of(new StrategyVersion(request.expectedVersion()))));
        return StrategyResponse.from(result);
    }

    private SaveStrategy.Command command(StrategyId strategyId, SaveStrategyRequest request, Optional<StrategyVersion> version) {
        return new SaveStrategy.Command(
            strategyId,
            new StrategyName(request.name()),
            request.scope(),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, request.eventType()),
            new StrategyExecutionPlan(request.executionPlan()),
            version,
            new UserToken(request.userToken()),
            new IdempotencyKey(request.idempotencyKey()),
            request.occurredAt() == null ? Instant.now() : request.occurredAt()
        );
    }

    private static void validate(SaveStrategyRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("strategy request must not be null");
        }
        requireText(request.strategyId(), "strategyId");
        requireText(request.name(), "name");
        if (request.scope() == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        requireText(request.eventType(), "eventType");
        requireText(request.executionPlan(), "executionPlan");
        requireText(request.userToken(), "userToken");
        requireText(request.idempotencyKey(), "idempotencyKey");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
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
