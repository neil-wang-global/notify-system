package com.example.notify.config;

import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.application.strategy.SaveStrategy;
import com.example.notify.domain.event.User;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.event.Users;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.notification.NotificationRecords;
import com.example.notify.domain.strategy.RuleAst;
import com.example.notify.domain.strategy.RuleOperator;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.StrategyExecutionPlan;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.matching.RuleAstEvaluator;
import com.example.notify.engine.timebox.TimeboxCounter;
import java.time.Duration;
import com.example.notify.infrastructure.persistence.JdbcNotificationExceptions;
import com.example.notify.infrastructure.persistence.JdbcNotificationRecords;
import com.example.notify.infrastructure.persistence.JdbcStrategies;
import com.example.notify.infrastructure.persistence.JdbcUserOperationExceptions;
import com.example.notify.infrastructure.persistence.PersistenceSchema;
import com.example.notify.interfaces.rest.EventsApi;
import com.example.notify.interfaces.rest.ExceptionsApi;
import com.example.notify.interfaces.rest.NotificationsApi;
import com.example.notify.interfaces.rest.StatusApi;
import com.example.notify.interfaces.rest.StrategiesApi;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class NotifyRuntimeConfig {

    @Bean
    PersistenceSchema persistenceSchema(JdbcTemplate jdbcTemplate) {
        PersistenceSchema schema = new PersistenceSchema(jdbcTemplate);
        schema.create();
        return schema;
    }

    @Bean
    Strategies strategies(JdbcTemplate jdbcTemplate) {
        return new JdbcStrategies(jdbcTemplate);
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
    ProcessUserOperationEvent processUserOperationEvent(NotificationRecords notificationRecords) {
        return new ProcessUserOperationEvent(new RuleAstEvaluator(), new TimeboxCounter(), event -> notificationRecords.addIfAbsent(NotificationRecord.from(event)));
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
    JdbcNotificationRecords notificationRecords(JdbcTemplate jdbcTemplate) {
        return new JdbcNotificationRecords(jdbcTemplate);
    }

    @Bean
    JdbcUserOperationExceptions userOperationExceptions(JdbcTemplate jdbcTemplate) {
        return new JdbcUserOperationExceptions(jdbcTemplate);
    }

    @Bean
    JdbcNotificationExceptions notificationExceptions(JdbcTemplate jdbcTemplate) {
        return new JdbcNotificationExceptions(jdbcTemplate);
    }

    @Bean
    NotificationsApi notificationsApi(JdbcNotificationRecords notificationRecords) {
        return new NotificationsApi(notificationRecords::list);
    }

    @Bean
    ExceptionsApi exceptionsApi(JdbcUserOperationExceptions userOperationExceptions, JdbcNotificationExceptions notificationExceptions) {
        return new ExceptionsApi(userOperationExceptions::list, notificationExceptions::list);
    }

    @Bean
    StatusApi statusApi() {
        return new StatusApi(StatusApi.StatusResponse.healthy());
    }

}
