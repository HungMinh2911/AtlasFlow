package com.atlasflow;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DatabaseTestController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseTestController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/db-test")
    public String testDatabase() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT 1",
                Integer.class
        );
        return "Database OK: " + result;
    }

    @GetMapping("/db-slow")
    public String slowDatabaseQuery(@org.springframework.web.bind.annotation.RequestParam(defaultValue = "5") int seconds) {
        jdbcTemplate.execute("SELECT pg_sleep(" + seconds + ")");
        return "Slow Query Finished after " + seconds + "s";
    }
}
