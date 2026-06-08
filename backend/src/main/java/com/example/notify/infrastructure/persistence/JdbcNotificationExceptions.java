package com.example.notify.infrastructure.persistence;

import com.example.notify.config.DataSourceRoleContext;
import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.exception.NotificationExceptionRecord;
import com.example.notify.domain.exception.NotificationExceptions;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.strategy.StrategyId;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcNotificationExceptions implements NotificationExceptions {

    private final JdbcTemplate jdbc;

    public JdbcNotificationExceptions(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbc = jdbc;
    }

    @Override
    public void add(NotificationExceptionRecord record) {
        DataSourceRoleContext.write(() -> jdbc.update("""
                insert into notification_exception_records (
                    id, notification_id, strategy_id, customer_id, event_id, payload,
                    failure_reason, retry_count, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            record.id(), record.notificationId().toString(), record.strategyId().toString(), record.customerId().toString(),
            record.eventId().toString(), record.payload(), record.failureReason(), record.retryCount(), record.status(),
            Timestamp.from(record.createdAt()), Timestamp.from(record.updatedAt())));
    }

    public List<NotificationExceptionRecord> list() {
        return DataSourceRoleContext.read(() -> jdbc.query("""
                    select id, notification_id, strategy_id, customer_id, event_id, payload,
                           failure_reason, retry_count, status, created_at, updated_at
                    from notification_exception_records order by created_at
                    """,
                (rs, rowNum) -> new NotificationExceptionRecord(
                    rs.getString("id"),
                    new NotificationId(rs.getString("notification_id")),
                    new StrategyId(rs.getString("strategy_id")),
                    new CustomerId(rs.getString("customer_id")),
                    new EventId(rs.getString("event_id")),
                    rs.getString("payload"),
                    rs.getString("failure_reason"),
                    rs.getInt("retry_count"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                )));
    }

    @Override
    public Optional<NotificationExceptionRecord> find(String id) {
        return DataSourceRoleContext.read(() -> jdbc.query("""
                    select id, notification_id, strategy_id, customer_id, event_id, payload,
                           failure_reason, retry_count, status, created_at, updated_at
                    from notification_exception_records where id = ?
                    """,
                (rs, rowNum) -> new NotificationExceptionRecord(
                    rs.getString("id"),
                    new NotificationId(rs.getString("notification_id")),
                    new StrategyId(rs.getString("strategy_id")),
                    new CustomerId(rs.getString("customer_id")),
                    new EventId(rs.getString("event_id")),
                    rs.getString("payload"),
                    rs.getString("failure_reason"),
                    rs.getInt("retry_count"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ), id).stream().findFirst());
    }

}
