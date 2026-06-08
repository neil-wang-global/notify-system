package com.example.notify.infrastructure.persistence;

import com.example.notify.config.DataSourceRoleContext;
import com.example.notify.domain.event.CustomerId;
import com.example.notify.domain.event.EventId;
import com.example.notify.domain.event.EventType;
import com.example.notify.domain.exception.UserOperationExceptionRecord;
import com.example.notify.domain.exception.UserOperationExceptions;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcUserOperationExceptions implements UserOperationExceptions {

    private final JdbcTemplate jdbc;

    public JdbcUserOperationExceptions(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbc = jdbc;
    }

    @Override
    public void add(UserOperationExceptionRecord record) {
        DataSourceRoleContext.write(() -> jdbc.update("""
                insert into user_operation_exception_records (
                    id, event_id, customer_id, event_type, payload, failure_reason,
                    retry_count, status, created_at, updated_at
                ) values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
            record.id(), record.eventId().toString(), record.customerId().toString(), record.eventType().toString(),
            record.payload(), record.failureReason(), record.retryCount(), record.status(),
            Timestamp.from(record.createdAt()), Timestamp.from(record.updatedAt())));
    }

    public List<UserOperationExceptionRecord> list() {
        return DataSourceRoleContext.read(() -> jdbc.query("""
                    select id, event_id, customer_id, event_type, payload, failure_reason,
                           retry_count, status, created_at, updated_at
                    from user_operation_exception_records order by created_at
                    """,
                (rs, rowNum) -> new UserOperationExceptionRecord(
                    rs.getString("id"),
                    new EventId(rs.getString("event_id")),
                    new CustomerId(rs.getString("customer_id")),
                    new EventType(rs.getString("event_type")),
                    rs.getString("payload"),
                    rs.getString("failure_reason"),
                    rs.getInt("retry_count"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                )));
    }

    @Override
    public Optional<UserOperationExceptionRecord> find(String id) {
        return DataSourceRoleContext.read(() -> jdbc.query("""
                    select id, event_id, customer_id, event_type, payload, failure_reason,
                           retry_count, status, created_at, updated_at
                    from user_operation_exception_records where id = ?
                    """,
                (rs, rowNum) -> new UserOperationExceptionRecord(
                    rs.getString("id"),
                    new EventId(rs.getString("event_id")),
                    new CustomerId(rs.getString("customer_id")),
                    new EventType(rs.getString("event_type")),
                    rs.getString("payload"),
                    rs.getString("failure_reason"),
                    rs.getInt("retry_count"),
                    rs.getString("status"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("updated_at").toInstant()
                ), id).stream().findFirst());
    }

}
