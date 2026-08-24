package com.atlasflow;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseTestControllerTest {

    @Test
    void databaseTestReturnsQueryResult() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        DatabaseTestController controller = new DatabaseTestController(jdbcTemplate);

        assertEquals("Database OK: 1", controller.testDatabase());
    }
}
