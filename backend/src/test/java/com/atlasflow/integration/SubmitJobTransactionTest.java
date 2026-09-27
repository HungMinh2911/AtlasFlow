package com.atlasflow.integration;

import com.atlasflow.job.api.CreateJobRequest;
import com.atlasflow.job.api.JobResponse;
import com.atlasflow.job.application.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SubmitJobTransactionTest extends AbstractBaseIntegrationTest {

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void submitJobShouldAtomicallyPersistJobAndOutboxEventInTestcontainerPostgres() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String idempotencyKey = "key-submit-tc";

        jdbcTemplate.update("INSERT INTO organizations (id, name) VALUES (?, ?)", orgId, "Submit Org TC");
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, status) VALUES (?, ?, ?, 'ACTIVE')", projectId, orgId, "Submit Proj TC");
        jdbcTemplate.update("INSERT INTO workflows (id, project_id, version, status) VALUES (?, ?, 1, 'PUBLISHED')", workflowId, projectId);
        jdbcTemplate.update("INSERT INTO files (id, project_id, file_name, object_key, checksum, size_bytes, status) VALUES (?, ?, 'in.txt', ?, 'sum', 10, 'AVAILABLE')", fileId, projectId, "key/submit-" + UUID.randomUUID());

        CreateJobRequest request = new CreateJobRequest(projectId, workflowId, fileId);
        JobResponse response = jobApplicationService.createJob(request, idempotencyKey);

        assertNotNull(response.id());
        assertEquals("SUBMITTED", response.status());

        // Verify row counts in PostgreSQL Testcontainer
        Long jobCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jobs WHERE id = ?", Long.class, response.id());
        Long outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?", Long.class, response.id());

        assertEquals(1L, jobCount);
        assertEquals(1L, outboxCount);
    }
}
