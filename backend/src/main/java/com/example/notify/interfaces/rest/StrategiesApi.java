package com.example.notify.interfaces.rest;

import com.example.notify.application.strategy.SaveStrategy;
import com.example.notify.domain.event.UserToken;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleConnector;
import com.example.notify.domain.strategy.RuleGroup;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.RuleValue;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyRuleItem;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import java.time.Instant;
import java.util.List;
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
        RuleAst ruleAst = ruleAst(request);
        return new SaveStrategy.Command(
            strategyId,
            new StrategyName(request.name()),
            request.scope(),
            ruleAst,
            new StrategyExecutionPlan(request.executionPlan()),
            version,
            new UserToken(request.userToken()),
            new IdempotencyKey(request.idempotencyKey()),
            request.occurredAt() == null ? Instant.now() : request.occurredAt()
        );
    }

    private RuleAst ruleAst(SaveStrategyRequest request) {
        if (request.rules() != null && !request.rules().isEmpty()) {
            List<StrategyRuleItem> items = request.rules().stream()
                .map(this::toRuleItem)
                .toList();
            return RuleAst.fromRows(items);
        }
        return new RuleAst.Comparison("eventType", RuleOperator.EQ, request.eventType());
    }

    private StrategyRuleItem toRuleItem(RuleRowRequest row) {
        RuleValue value = row.value() instanceof List<?> list
            ? list.stream().allMatch(Number.class::isInstance)
                ? RuleValue.ofNumbers((Number) list.getFirst(), (Number) list.get(1), list.subList(2, list.size()).stream().map(n -> (Number) n).toArray(Number[]::new))
                : RuleValue.ofStrings(String.valueOf(list.getFirst()), String.valueOf(list.get(1)), list.subList(2, list.size()).stream().map(String::valueOf).toArray(String[]::new))
            : row.value() instanceof Number num
                ? RuleValue.ofNumber(num)
                : RuleValue.ofString(String.valueOf(row.value()));
        return StrategyRuleItem.condition(
            row.field(),
            RuleOperator.valueOf(row.operator()),
            value,
            RuleConnector.valueOf(row.connector()),
            new RuleGroup(row.group()),
            row.sortOrder()
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
        if ((request.rules() == null || request.rules().isEmpty()) && (request.eventType() == null || request.eventType().isBlank())) {
            throw new IllegalArgumentException("eventType or rules must not be blank");
        }
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
        List<RuleRowRequest> rules,
        String executionPlan,
        int expectedVersion,
        String userToken,
        String idempotencyKey,
        Instant occurredAt
    ) {

    }

    public record RuleRowRequest(
        String field,
        String operator,
        Object value,
        String connector,
        String group,
        int sortOrder
    ) {

    }

    public record StrategyResponse(String strategyId, int version) {

        private static StrategyResponse from(SaveStrategy.Result result) {
            return new StrategyResponse(result.strategy().id().toString(), result.strategy().version().value());
        }

    }

}
