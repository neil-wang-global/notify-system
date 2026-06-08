package com.example.notify.config;

import com.example.notify.application.notification.PersistNotification;
import com.example.notify.application.port.CacheInvalidationPort;
import com.example.notify.application.event.ProcessUserOperationEvent;
import com.example.notify.application.event.StrategySavedListener;
import com.example.notify.application.strategy.SaveStrategy;
import com.example.notify.domain.event.User;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.event.Users;
import com.example.notify.domain.notification.NotificationRecords;
import com.example.notify.domain.strategy.Strategies;
import com.example.notify.domain.strategy.StrategyId;
import com.example.notify.engine.matching.RuleAstEvaluator;
import com.example.notify.engine.timebox.TimeboxCounter;
import com.example.notify.engine.timebox.TimeboxOperations;
import com.example.notify.infrastructure.cache.CacheStrategies;
import com.example.notify.infrastructure.cache.CacheStrategy;
import com.example.notify.infrastructure.persistence.JdbcNotificationExceptions;
import com.example.notify.infrastructure.persistence.JdbcNotificationRecords;
import com.example.notify.infrastructure.persistence.JdbcStrategies;
import com.example.notify.infrastructure.persistence.JdbcUserOperationExceptions;
import com.example.notify.infrastructure.persistence.PersistenceSchema;
import com.example.notify.engine.matching.CandidateStrategyLookup;
import com.example.notify.infrastructure.redis.RealRedisStrategies;
import com.example.notify.infrastructure.redis.RedisStrategies;
import com.example.notify.infrastructure.redis.RedisTimeboxCounter;
import com.example.notify.interfaces.rest.EventsApi;
import com.example.notify.interfaces.rest.ExceptionsApi;
import com.example.notify.interfaces.rest.NotificationsApi;
import com.example.notify.interfaces.rest.StatusApi;
import com.example.notify.interfaces.rest.StrategiesApi;
import com.example.notify.domain.notification.NotificationEvents;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
@EnableConfigurationProperties(NotifyProperties.class)
public class NotifyRuntimeConfig {

    @Bean PersistenceSchema persistenceSchema(JdbcTemplate jdbcTemplate) { PersistenceSchema s = new PersistenceSchema(jdbcTemplate); s.create(); return s; }
    @Bean Strategies strategies(JdbcTemplate jdbcTemplate, org.springframework.transaction.PlatformTransactionManager transactionManager) { return new JdbcStrategies(jdbcTemplate, transactionManager); }
    @Bean Users users() { return token -> new User(new UserId(token.toString()), java.util.List.of()); }
    @Bean SaveStrategy saveStrategy(Strategies strategies, Users users) { return new SaveStrategy(strategies, users); }

    @Bean
    ProcessUserOperationEvent processUserOperationEvent(TimeboxOperations timeboxOperations, PersistNotification persistNotification, @Autowired(required = false) NotificationEvents notificationEvents) {
        NotificationEvents effective = notificationEvents != null ? notificationEvents : persistNotification::persist;
        return new ProcessUserOperationEvent(new RuleAstEvaluator(), timeboxOperations, effective);
    }

    @Bean @ConditionalOnBean(StringRedisTemplate.class) TimeboxOperations redisTimeboxCounter(StringRedisTemplate redis, DegradationState degradationState) { return new RedisTimeboxCounter(redis, degradationState); }
    @Bean @ConditionalOnMissingBean(TimeboxOperations.class) TimeboxOperations inMemoryTimeboxCounter() { return new TimeboxCounter(); }

    // --- Caffeine local cache layer ---

    @Bean
    Cache<StrategyId, CacheStrategy> strategyLocalCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofMinutes(10))
            .build();
    }

    @Bean
    Cache<CacheStrategies.CandidateKey, Set<StrategyId>> candidateCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(Duration.ofSeconds(10))
            .build();
    }

    @Bean
    CandidateStrategyLookup candidateStrategyLookup(@Autowired(required = false) StringRedisTemplate redis, DegradationState degradationState, Cache<StrategyId, CacheStrategy> strategyLocalCache, Cache<CacheStrategies.CandidateKey, Set<StrategyId>> candidateCache) {
        CandidateStrategyLookup delegate = redis != null ? new RealRedisStrategies(redis, degradationState) : new RedisStrategies();
        return new CacheStrategies(delegate, strategyLocalCache, candidateCache);
    }

    @Bean
    StrategySavedListener strategySavedListener(CacheInvalidationPort cacheInvalidation) {
        return new StrategySavedListener(cacheInvalidation);
    }

    // --- REST API beans ---

    @Bean
    StrategiesApi strategiesApi(SaveStrategy saveStrategy, Strategies strategies, CandidateStrategyLookup candidateLookup, NotifyProperties properties, org.springframework.context.ApplicationEventPublisher eventPublisher) {
        return new StrategiesApi(saveStrategy, strategies, candidateLookup, properties, eventPublisher);
    }

    @Bean
    EventsApi eventsApi(ProcessUserOperationEvent processUserOperationEvent, CandidateStrategyLookup candidateLookup, Strategies strategies) {
        return new EventsApi(processUserOperationEvent, candidateLookup, strategies);
    }

    @Bean JdbcNotificationRecords notificationRecords(JdbcTemplate jdbc) { return new JdbcNotificationRecords(jdbc); }
    @Bean PersistNotification persistNotification(NotificationRecords notificationRecords) { return new PersistNotification(notificationRecords); }
    @Bean JdbcUserOperationExceptions userOperationExceptions(JdbcTemplate jdbc) { return new JdbcUserOperationExceptions(jdbc); }
    @Bean JdbcNotificationExceptions notificationExceptions(JdbcTemplate jdbc) { return new JdbcNotificationExceptions(jdbc); }
    @Bean NotificationsApi notificationsApi(JdbcNotificationRecords records) { return new NotificationsApi(records::list); }
    @Bean ExceptionsApi exceptionsApi(JdbcUserOperationExceptions uo, JdbcNotificationExceptions ne) { return new ExceptionsApi(uo::list, ne::list); }
    @Bean DegradationState degradationState() { return new DegradationState(); }

    @Bean
    StatusApi statusApi(DegradationState degradationState, @Autowired(required = false) org.springframework.kafka.core.KafkaAdmin kafkaAdmin, @Autowired(required = false) StringRedisTemplate redisTemplate, CandidateStrategyLookup candidateLookup) {
        Supplier<String> kafkaStatus = () -> { if (kafkaAdmin == null) return "DISABLED"; try { kafkaAdmin.clusterId(); return "RUNNING"; } catch (Exception e) { return "DOWN"; } };
        Supplier<String> redisStatus = () -> { if (redisTemplate == null) return "DISABLED"; try { redisTemplate.getConnectionFactory().getConnection().ping(); return "HEALTHY"; } catch (Exception e) { return "DOWN"; } };
        Supplier<String> strategyCacheVersion = () -> "available";
        return new StatusApi(kafkaStatus, redisStatus, strategyCacheVersion, degradationState);
    }

}
