package com.example.notify.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UserSchemaTest {

    @Test
    void declaresLightweightUserAndGroupTables() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/schema.sql"));

        assertTrue(schema.contains("create table users"));
        assertTrue(schema.contains("create table user_groups"));
        assertTrue(schema.contains("create table user_group_members"));
    }

}
