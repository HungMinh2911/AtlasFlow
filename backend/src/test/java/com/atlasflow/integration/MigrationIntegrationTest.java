package com.atlasflow.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MigrationIntegrationTest extends AbstractBaseIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void flywayMigrationsShouldApplySuccessfullyOnFreshDatabase() {
        Integer tableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = 'public'",
                Integer.class
        );

        assertTrue(tableCount != null && tableCount >= 10, "Flyway baseline migration should create all domain tables");
    }
}
