package com.example.notify.infrastructure.persistence;

import org.springframework.jdbc.core.JdbcTemplate;

public final class PersistenceSchema {

    private final JdbcTemplate jdbc;

    public PersistenceSchema(JdbcTemplate jdbc) {
        if (jdbc == null) {
            throw new IllegalArgumentException("jdbcTemplate must not be null");
        }
        this.jdbc = jdbc;
    }

    public void create() {
        jdbc.execute("""
            create table if not exists users (
                id varchar(128) primary key,
                user_token varchar(256) not null unique
            )
            """);
        jdbc.execute("""
            create table if not exists user_groups (
                id varchar(128) primary key,
                name varchar(256) not null
            )
            """);
        jdbc.execute("""
            create table if not exists user_group_members (
                user_id varchar(128) not null,
                user_group_id varchar(128) not null,
                primary key (user_id, user_group_id)
            )
            """);
        jdbc.execute("""
            create table if not exists strategies (
                id varchar(128) primary key,
                name varchar(256) not null,
                scope_kind varchar(64) not null,
                rule_field varchar(128) not null,
                rule_operator varchar(64) not null,
                rule_value varchar(512) not null,
                rule_ast_json text,
                window_size_seconds bigint not null,
                shard_size_seconds bigint not null,
                business_dedup_seconds bigint not null,
                dedup_fields_json text,
                threshold integer not null default 1,
                version integer not null
            )
            """);
        jdbc.execute("alter table strategies add column if not exists rule_ast_json text");
        jdbc.execute("alter table strategies add column if not exists threshold integer default 1");
        jdbc.execute("alter table strategies add column if not exists dedup_fields_json text");
        jdbc.execute("""
            create table if not exists strategy_rule_items (
                strategy_id varchar(128) not null,
                sort_order integer not null,
                group_id varchar(128) not null,
                connector varchar(64) not null,
                field varchar(128) not null,
                operator varchar(64) not null,
                value_type varchar(64) not null,
                value_json text not null,
                primary key (strategy_id, sort_order)
            )
            """);
        jdbc.execute("""
            create table if not exists strategy_scope_ids (
                strategy_id varchar(128) not null,
                id_kind varchar(16) not null,
                scope_id varchar(128) not null,
                primary key (strategy_id, id_kind, scope_id)
            )
            """);
        jdbc.execute("""
            create table if not exists strategy_idempotency_keys (
                idempotency_key varchar(256) primary key,
                strategy_id varchar(128) not null,
                fingerprint varchar(512) not null
            )
            """);
        jdbc.execute("""
            create table if not exists notification_records (
                notification_id varchar(128) primary key,
                strategy_id varchar(128) not null,
                customer_id varchar(128) not null,
                user_id varchar(128) not null,
                event_id varchar(128) not null,
                event_type varchar(128) not null,
                triggered_at timestamp with time zone not null,
                window_value varchar(64) not null,
                threshold_value integer not null,
                current_count integer not null,
                dedupe_key varchar(512) not null unique
            )
            """);
        jdbc.execute("""
            create table if not exists user_operation_exception_records (
                id varchar(128) primary key,
                event_id varchar(128) not null,
                customer_id varchar(128) not null,
                event_type varchar(128) not null,
                payload text not null,
                failure_reason text not null,
                retry_count integer not null,
                status varchar(64) not null,
                created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null
            )
            """);
        jdbc.execute("""
            create table if not exists notification_exception_records (
                id varchar(128) primary key,
                notification_id varchar(128) not null,
                strategy_id varchar(128) not null,
                customer_id varchar(128) not null,
                event_id varchar(128) not null,
                payload text not null,
                failure_reason text not null,
                retry_count integer not null,
                status varchar(64) not null,
                created_at timestamp with time zone not null,
                updated_at timestamp with time zone not null
            )
            """);

        // T-29: indexes for common query patterns
        createIndexIfNotExists("idx_strategies_version", "strategies (version)");
        createIndexIfNotExists("idx_notification_records_strategy_id", "notification_records (strategy_id)");
        createIndexIfNotExists("idx_notification_records_customer_id", "notification_records (customer_id)");
        createIndexIfNotExists("idx_notification_records_triggered_at", "notification_records (triggered_at)");
        createIndexIfNotExists("idx_strategy_idempotency_keys_strategy_id", "strategy_idempotency_keys (strategy_id)");
    }

    private void createIndexIfNotExists(String indexName, String columns) {
        try {
            jdbc.execute("create index if not exists " + indexName + " on " + columns);
        } catch (Exception ignored) {
            // Some databases may not support IF NOT EXISTS for indexes; safe to skip
        }
    }

}
