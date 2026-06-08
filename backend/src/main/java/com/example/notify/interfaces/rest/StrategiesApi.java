package com.example.notify.interfaces.rest;

import com.example.notify.application.strategy.SaveStrategy;
import com.example.notify.config.NotifyProperties;
import com.example.notify.domain.event.UserGroupId;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.event.UserToken;
import com.example.notify.domain.strategy.IdempotencyKey;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleConnector;
import com.example.notify.domain.strategy.RuleGroup;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.RuleValue;
import com.example.notify.domain.strategy.Strategy;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.domain.strategy.StrategyName;
import com.example.notify.domain.strategy.StrategyRuleItem;
import com.example.notify.domain.strategy.StrategyScope;
import com.example.notify.domain.strategy.StrategyVersion;
import com.example.notify.domain.strategy.StrategySaved;
import com.example.notify.engine.matching.CandidateStrategyLookup;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/strategies")
public final class StrategiesApi {

    private static final Duration DEFAULT_BUSINESS_DEDUP_WINDOW = Duration.ZERO;
    private static final List<String> DEFAULT_DEDUP_FIELDS = List.of("customerId", "userId", "eventType");

    private static final Map<String, Duration> WINDOW_DURATIONS = Map.of(
        "5m", Duration.ofMinutes(5),
        "10m", Duration.ofMinutes(10),
        "30m", Duration.ofMinutes(30),
        "1d", Duration.ofDays(1),
        "5d", Duration.ofDays(5)
    );

    private static final Map<Duration, String> WINDOW_KEYS = WINDOW_DURATIONS.entrySet().stream()
        .collect(java.util.stream.Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    private static String formatWindowSize(Duration d) {
        String key = WINDOW_KEYS.get(d);
        if (key != null) {
            return key;
        }
        if (d.toDays() > 0) return d.toDays() + "d";
        if (d.toHours() > 0) return d.toHours() + "h";
        if (d.toMinutes() > 0) return d.toMinutes() + "m";
        return d.toSeconds() + "s";
    }

    private final SaveStrategy saveStrategy;
    private final Strategies strategies;
    private final CandidateStrategyLookup candidateLookup;
    private final NotifyProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public StrategiesApi(SaveStrategy saveStrategy, Strategies strategies, CandidateStrategyLookup candidateLookup, NotifyProperties properties, ApplicationEventPublisher eventPublisher) {
        if (saveStrategy == null || strategies == null || candidateLookup == null || properties == null || eventPublisher == null) {
            throw new IllegalArgumentException("strategies api is incomplete");
        }
        this.saveStrategy = saveStrategy;
        this.strategies = strategies;
        this.candidateLookup = candidateLookup;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @GetMapping("/window-presets")
    public List<WindowPreset> windowPresets() {
        return WINDOW_DURATIONS.entrySet().stream()
            .map(e -> new WindowPreset(e.getKey(), e.getValue().toString()))
            .toList();
    }

    @GetMapping
    public List<StrategyListResponse> list() {
        return strategies.list().stream()
            .map(StrategyListResponse::from)
            .toList();
    }

    @GetMapping("/{strategyId}")
    public StrategyDetailResponse get(@PathVariable String strategyId) {
        return strategies.find(new StrategyId(strategyId))
            .map(StrategyDetailResponse::from)
            .orElseThrow(() -> new IllegalArgumentException("strategy not found: " + strategyId));
    }

    @PostMapping
    public StrategySaveResponse save(@RequestBody SaveStrategyRequest request) {
        validate(request);
        SaveStrategy.Result result = saveStrategy.save(command(new StrategyId(request.strategyId()), request, Optional.empty()));
        candidateLookup.refresh(result.strategy());
        eventPublisher.publishEvent(new StrategySaved(result.strategy().id(), result.strategy().version(), Instant.now()));
        return StrategySaveResponse.from(result);
    }

    @PutMapping("/{strategyId}")
    public StrategySaveResponse update(@PathVariable String strategyId, @RequestBody SaveStrategyRequest request) {
        validate(request);
        if (!strategyId.equals(request.strategyId())) {
            throw new IllegalArgumentException("path strategyId must match request strategyId");
        }
        SaveStrategy.Result result = saveStrategy.save(command(new StrategyId(strategyId), request, Optional.of(new StrategyVersion(request.expectedVersion()))));
        candidateLookup.refresh(result.strategy());
        eventPublisher.publishEvent(new StrategySaved(result.strategy().id(), result.strategy().version(), Instant.now()));
        return StrategySaveResponse.from(result);
    }

    @DeleteMapping("/{strategyId}")
    public void delete(@PathVariable String strategyId) {
        StrategyId id = new StrategyId(strategyId);
        strategies.find(id).orElseThrow(() -> new IllegalArgumentException("strategy not found: " + strategyId));
        candidateLookup.evict(id);
        strategies.delete(id);
    }

    private SaveStrategy.Command command(StrategyId strategyId, SaveStrategyRequest request, Optional<StrategyVersion> version) {
        RuleAst ruleAst = ruleAst(request);
        Duration windowSize = parseWindowSize(request.windowSize());
        Duration shardSize = properties.window() != null ? properties.window().shardSizeFor(windowSize) : Duration.ofSeconds(10);
        Duration businessDedupWindow = request.businessDedupWindowSeconds() != null
            ? Duration.ofSeconds(request.businessDedupWindowSeconds())
            : defaultBusinessDedupWindow();
        List<String> dedupFields = request.dedupFields() != null && !request.dedupFields().isEmpty()
            ? request.dedupFields()
            : DEFAULT_DEDUP_FIELDS;
        return new SaveStrategy.Command(
            strategyId,
            new StrategyName(request.name()),
            toDomainScope(request.scope()),
            ruleAst,
            new StrategyExecutionPlan(windowSize, shardSize, businessDedupWindow, dedupFields),
            request.threshold(),
            version,
            new UserToken(request.userToken()),
            new IdempotencyKey(request.idempotencyKey()),
            request.occurredAt() == null ? Instant.now() : request.occurredAt()
        );
    }

    private Duration defaultBusinessDedupWindow() {
        if (properties.deduplication() != null && properties.deduplication().defaultWindow() != null) {
            return properties.deduplication().defaultWindow();
        }
        return DEFAULT_BUSINESS_DEDUP_WINDOW;
    }

    private Duration parseWindowSize(String windowSize) {
        if (windowSize == null || windowSize.isBlank()) {
            if (properties.window() != null && properties.window().defaultSize() != null) {
                return properties.window().defaultSize();
            }
            return Duration.ofSeconds(30);
        }
        Duration duration = WINDOW_DURATIONS.get(windowSize);
        if (duration == null) {
            throw new IllegalArgumentException("unsupported windowSize: " + windowSize + ". Supported values: " + WINDOW_DURATIONS.keySet());
        }
        return duration;
    }

    private StrategyScope toDomainScope(ScopeDto scope) {
        if (scope == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        return switch (scope.kind()) {
            case GLOBAL -> StrategyScope.global();
            case USERS -> StrategyScope.users(scope.userIds().stream().map(UserId::new).toArray(UserId[]::new));
            case USER_GROUPS -> StrategyScope.userGroups(scope.userGroupIds().stream().map(UserGroupId::new).toArray(UserGroupId[]::new));
        };
    }

    private RuleAst ruleAst(SaveStrategyRequest request) {
        RuleAst eventTypeNode = new RuleAst.Comparison("eventType", RuleOperator.EQ, request.eventType());
        if (request.rules() == null || request.rules().isEmpty()) {
            return eventTypeNode;
        }
        List<StrategyRuleItem> items = request.rules().stream()
            .map(this::toRuleItem)
            .toList();
        RuleAst rulesTree = RuleAst.fromRows(items);
        return new RuleAst.Group(RuleConnector.AND, List.of(eventTypeNode, rulesTree));
    }

    private StrategyRuleItem toRuleItem(RuleRowRequest row) {
        RuleValue value;
        if (row.value() instanceof List<?> list) {
            if (list.size() < 2) {
                throw new IllegalArgumentException("rule value list must contain at least 2 elements, got " + list.size());
            }
            value = list.stream().allMatch(Number.class::isInstance)
                ? RuleValue.ofNumbers((Number) list.getFirst(), (Number) list.get(1), list.subList(2, list.size()).stream().map(n -> (Number) n).toArray(Number[]::new))
                : RuleValue.ofStrings(String.valueOf(list.getFirst()), String.valueOf(list.get(1)), list.subList(2, list.size()).stream().map(String::valueOf).toArray(String[]::new));
        } else if (row.value() instanceof Number num) {
            value = RuleValue.ofNumber(num);
        } else {
            value = RuleValue.ofString(String.valueOf(row.value()));
        }
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
        if (request.scope() == null || request.scope().kind() == null) {
            throw new IllegalArgumentException("scope must not be null");
        }
        if (request.eventType() == null || request.eventType().isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        requireText(request.userToken(), "userToken");
        requireText(request.idempotencyKey(), "idempotencyKey");
        if (request.threshold() < 1) {
            throw new IllegalArgumentException("threshold must be at least 1, got " + request.threshold());
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }

    public record WindowPreset(String key, String duration) {}

    public record SaveStrategyRequest(
        String strategyId,
        String name,
        ScopeDto scope,
        String eventType,
        List<RuleRowRequest> rules,
        String windowSize,
        int expectedVersion,
        String userToken,
        String idempotencyKey,
        Instant occurredAt,
        int threshold,
        Long businessDedupWindowSeconds,
        List<String> dedupFields
    ) {
        public SaveStrategyRequest {
            // threshold validation is handled in validate()
        }
    }

    public record ScopeDto(
        StrategyScope.Kind kind,
        List<String> userIds,
        List<String> userGroupIds
    ) {
        public ScopeDto {
            if (userIds == null) {
                userIds = List.of();
            }
            if (userGroupIds == null) {
                userGroupIds = List.of();
            }
        }
    }

    public record RuleRowRequest(
        String field,
        String operator,
        Object value,
        String connector,
        String group,
        int sortOrder
    ) {}

    public record StrategySaveResponse(String strategyId, int version) {
        private static StrategySaveResponse from(SaveStrategy.Result result) {
            return new StrategySaveResponse(result.strategy().id().toString(), result.strategy().version().value());
        }
    }

    public record StrategyListResponse(String strategyId, String name, ScopeResponse scope, String eventType, String windowSize, int threshold, int version) {
        static StrategyListResponse from(Strategy s) {
            String eventType = extractEventType(s.ruleAst());
            String windowSize = formatWindowSize(s.executionPlan().windowSize());
            return new StrategyListResponse(s.id().toString(), s.name().value(), ScopeResponse.from(s.scope()), eventType, windowSize, s.threshold(), s.version().value());
        }
    }

    public record StrategyDetailResponse(
        String strategyId, String name, ScopeResponse scope, String eventType, String windowSize,
        int threshold, int version, Object ruleAst, String executionPlan, List<RuleItemResponse> rules,
        long businessDedupWindowSeconds, List<String> dedupFields
    ) {
        static StrategyDetailResponse from(Strategy s) {
            String eventType = extractEventType(s.ruleAst());
            String windowSize = formatWindowSize(s.executionPlan().windowSize());
            return new StrategyDetailResponse(
                s.id().toString(), s.name().value(), ScopeResponse.from(s.scope()), eventType, windowSize,
                s.threshold(), s.version().value(), s.ruleAst(), s.executionPlan().toString(), RuleItemResponse.from(s.ruleAst()),
                s.executionPlan().businessDedupWindow().toSeconds(),
                s.executionPlan().dedupFields()
            );
        }
    }

    public record ScopeResponse(String kind, List<String> userIds, List<String> userGroupIds) {
        static ScopeResponse from(StrategyScope scope) {
            return new ScopeResponse(
                scope.kind().name(),
                scope.userIds().stream().map(UserId::value).toList(),
                scope.userGroupIds().stream().map(UserGroupId::value).toList()
            );
        }
    }

    public record RuleItemResponse(String field, String operator, Object value, String connector, String group, int sortOrder) {
        static List<RuleItemResponse> from(RuleAst ast) {
            return ruleRows(ast, "default", RuleConnector.AND, new java.util.concurrent.atomic.AtomicInteger(1))
                .stream()
                .filter(r -> !"eventType".equals(r.field))
                .toList();
        }

        private static List<RuleItemResponse> ruleRows(RuleAst ast, String groupId, RuleConnector connector, java.util.concurrent.atomic.AtomicInteger sortOrder) {
            return switch (ast) {
                case RuleAst.Comparison c -> List.of(new RuleItemResponse(c.field(), c.operator().name(), c.value(), connector.name(), groupId, sortOrder.getAndIncrement()));
                case RuleAst.Not n -> ruleRows(n.child(), groupId, connector, sortOrder);
                case RuleAst.Group g -> g.children().stream()
                    .flatMap(child -> ruleRows(child, groupId, g.connector(), sortOrder).stream())
                    .toList();
            };
        }
    }

    private static String extractEventType(RuleAst ast) {
        return switch (ast) {
            case RuleAst.Comparison c when "eventType".equals(c.field()) -> String.valueOf(c.value());
            case RuleAst.Comparison c -> "";
            case RuleAst.Group g -> {
                String result = "";
                for (RuleAst child : g.children()) {
                    result = extractEventType(child);
                    if (!result.isEmpty()) break;
                }
                yield result;
            }
            case RuleAst.Not n -> extractEventType(n.child());
        };
    }

}
