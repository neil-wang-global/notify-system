create table if not exists notification_records (
    notification_id varchar(128) primary key,
    strategy_id varchar(128) not null,
    customer_id varchar(128) not null,
    user_id varchar(128) not null,
    event_id varchar(128) not null,
    event_type varchar(128) not null,
    triggered_at timestamp with time zone not null,
    payload text not null,
    dedupe_key varchar(512) not null unique
);

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
);

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
);
