package com.example.notify.config;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.application.strategy.SaveStrategy;
import com.example.notify.domain.event.User;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.event.Users;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.matching.RuleAstEvaluator;
import com.example.notify.engine.timebox.TimeboxCounter;
import java.time.Duration;
import com.example.notify.infrastructure.persistence.DbStrategies;
import com.example.notify.interfaces.rest.EventsApi;
import com.example.notify.interfaces.rest.ExceptionsApi;
import com.example.notify.interfaces.rest.NotificationsApi;
import com.example.notify.interfaces.rest.StatusApi;
import com.example.notify.interfaces.rest.StrategiesApi;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NotifyRuntimeConfig {

    private final List<NotificationEvent> notificationEvents = new CopyOnWriteArrayList<>();

    @Bean
    Strategies strategies() {
        return new DbStrategies();
    }

    @Bean
    Users users() {
        return userToken -> new User(new UserId(userToken.toString()), List.of());
    }

    @Bean
    SaveStrategy saveStrategy(Strategies strategies, Users users) {
        return new SaveStrategy(strategies, users);
    }

    @Bean
    ProcessUserOperationEvent processUserOperationEvent() {
        return new ProcessUserOperationEvent(new RuleAstEvaluator(), new TimeboxCounter(), notificationEvents::add);
    }

    @Bean
    StrategiesApi strategiesApi(SaveStrategy saveStrategy) {
        return new StrategiesApi(saveStrategy);
    }

    @Bean
    EventsApi eventsApi(ProcessUserOperationEvent processUserOperationEvent) {
        ProcessUserOperationEvent.MatchedStrategy defaultStrategy = new ProcessUserOperationEvent.MatchedStrategy(
            new StrategyId("strategy-runtime-default"),
            new RuleAst.Comparison("eventType", RuleOperator.EQ, "PRODUCT_VIEW"),
            new StrategyExecutionPlan(Duration.ofSeconds(30), Duration.ofSeconds(10), Duration.ZERO, List.of("customerId", "userId", "eventType")),
            1
        );
        return new EventsApi(processUserOperationEvent, List.of(defaultStrategy));
    }

    @Bean
    NotificationsApi notificationsApi() {
        return new NotificationsApi(() -> notificationEvents.stream()
            .map(event -> new NotificationRecord(event.notificationId(), event, event.triggeredAt()))
            .toList());
    }

    @Bean
    ExceptionsApi exceptionsApi() {
        return new ExceptionsApi(List::of, List::of);
    }

    @Bean
    StatusApi statusApi() {
        return new StatusApi(StatusApi.StatusResponse.healthy());
    }

}
