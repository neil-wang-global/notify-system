package com.example.notify.infrastructure.persistence;

import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.event.UserId;
import com.example.notify.domain.notification.NotificationEvent;
import com.example.notify.domain.notification.NotificationId;
import com.example.notify.domain.notification.NotificationRecord;
import com.example.notify.domain.notification.NotificationRecords;
import com.example.notify.domain.strategy.StrategyId;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcNotificationRecords implements NotificationRecords {

    private final JdbcTemplate jdbc;

    public JdbcNotificationRecords(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbc = jdbc;
    }

    @Override
    public boolean addIfAbsent(NotificationRecord record) {
        NotificationEvent event = record.event();
        try {
            jdbc.update("""
                    insert into notification_records (
                        notification_id, strategy_id, customer_id, user_id, event_id, event_type,
                        triggered_at, window_value, threshold_value, current_count, dedupe_key
                    ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                record.notificationId().toString(),
                event.strategyId().toString(),
                event.customerId().toString(),
                event.userId().toString(),
                event.eventId().toString(),
                event.eventType().toString(),
                event.triggeredAt(),
                event.window(),
                event.threshold(),
                event.currentCount(),
                event.dedupeKey()
            );
            return true;
        } catch (DuplicateKeyException ignored) {
            return false;
        }
    }

    public List<NotificationRecord> list() {
        return jdbc.query("""
                select notification_id, strategy_id, customer_id, user_id, event_id, event_type,
                       triggered_at, window_value, threshold_value, current_count, dedupe_key
                from notification_records order by triggered_at
                """,
            (rs, rowNum) -> NotificationRecord.from(new NotificationEvent(
                new NotificationId(rs.getString("notification_id")),
                new StrategyId(rs.getString("strategy_id")),
                new CustomerId(rs.getString("customer_id")),
                new UserId(rs.getString("user_id")),
                new EventId(rs.getString("event_id")),
                new EventType(rs.getString("event_type")),
                rs.getTimestamp("triggered_at").toInstant(),
                rs.getString("window_value"),
                rs.getInt("threshold_value"),
                rs.getInt("current_count"),
                rs.getString("dedupe_key")
            )));
    }

}
