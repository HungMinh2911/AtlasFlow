package com.atlasflow.integration;

import com.atlasflow.job.api.CreateJobRequest;
import com.atlasflow.job.application.JobApplicationService;
import com.atlasflow.outbox_events.application.port.OutboxEventsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class RollbackIntegrationTest extends AbstractBaseIntegrationTest {

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private OutboxEventsRepository outboxEventsRepository; // Mock outbox repo to trigger failure inside transaction

    @Test
    void createJobShouldRollbackBothJobAndOutboxWhenOutboxInsertFails() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String idempotencyKey = "key-rollback-tc";

        jdbcTemplate.update("INSERT INTO organizations (id, name) VALUES (?, ?)", orgId, "Rollback Org TC");
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, status) VALUES (?, ?, ?, 'ACTIVE')", projectId, orgId, "Rollback Proj TC");
        jdbcTemplate.update("INSERT INTO workflows (id, project_id, version, status) VALUES (?, ?, 1, 'PUBLISHED')", workflowId, projectId);
        jdbcTemplate.update("INSERT INTO files (id, project_id, file_name, object_key, checksum, size_bytes, status) VALUES (?, ?, 'in.txt', ?, 'sum', 10, 'AVAILABLE')", fileId, projectId, "key/rollback-" + UUID.randomUUID());

        // Simulate failure on outbox save
        when(outboxEventsRepository.save(any())).thenThrow(new RuntimeException("outbox-simulated-failure"));

        CreateJobRequest request = new CreateJobRequest(projectId, workflowId, fileId);

        assertThrows(RuntimeException.class, () -> jobApplicationService.createJob(request, idempotencyKey));

        // Verify transaction rollback in Postgres Testcontainer
        Long jobCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jobs WHERE project_id = ? AND idempotency_key = ?", Long.class, projectId, idempotencyKey);
        Long outboxCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE payload::text LIKE ?", Long.class, "%" + projectId + "%");

        assertEquals(0L, jobCount, "Job must not exist in database after rollback");
        assertEquals(0L, outboxCount, "Outbox event must not exist in database after rollback");
    }
}
