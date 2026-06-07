create table users (
    id varchar(128) primary key,
    user_token varchar(256) not null unique
);

create table user_groups (
    id varchar(128) primary key,
    name varchar(256) not null
);

create table user_group_members (
    user_id varchar(128) not null,
    user_group_id varchar(128) not null,
    primary key (user_id, user_group_id)
);
