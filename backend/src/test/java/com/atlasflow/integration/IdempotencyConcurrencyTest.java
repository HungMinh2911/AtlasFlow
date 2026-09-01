package com.atlasflow.integration;

import com.atlasflow.job.api.CreateJobRequest;
import com.atlasflow.job.api.JobResponse;
import com.atlasflow.job.application.JobApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IdempotencyConcurrencyTest extends AbstractBaseIntegrationTest {

    @Autowired
    private JobApplicationService jobApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void concurrentSubmissionsWithSameIdempotencyKeyShouldProduceExactlyOneJobAndOneOutboxEvent() throws Exception {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        String idempotencyKey = "key-concurrent-tc";

        jdbcTemplate.update("INSERT INTO organizations (id, name) VALUES (?, ?)", orgId, "Conc Org TC");
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, status) VALUES (?, ?, ?, 'ACTIVE')", projectId, orgId, "Conc Proj TC");
        jdbcTemplate.update("INSERT INTO workflows (id, project_id, version, status) VALUES (?, ?, 1, 'PUBLISHED')", workflowId, projectId);
        jdbcTemplate.update("INSERT INTO files (id, project_id, file_name, object_key, checksum, size_bytes, status) VALUES (?, ?, 'in.txt', ?, 'sum', 10, 'AVAILABLE')", fileId, projectId, "key/conc-" + UUID.randomUUID());

        CreateJobRequest request = new CreateJobRequest(projectId, workflowId, fileId);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        List<Callable<JobResponse>> tasks = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> jobApplicationService.createJob(request, idempotencyKey));
        }

        List<Future<JobResponse>> futures = executor.invokeAll(tasks);
        executor.shutdown();

        List<JobResponse> responses = new ArrayList<>();
        for (Future<JobResponse> f : futures) {
            responses.add(f.get());
        }

        UUID firstJobId = responses.get(0).id();
        for (JobResponse res : responses) {
            assertEquals(firstJobId, res.id(), "All concurrent requests must return the exact same Job ID");
        }

        Long totalJobs = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jobs WHERE project_id = ? AND idempotency_key = ?", Long.class, projectId, idempotencyKey);
        Long totalOutbox = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?", Long.class, firstJobId);

        assertEquals(1L, totalJobs, "Exactly one job should be created in Postgres");
        assertEquals(1L, totalOutbox, "Exactly one outbox event should be created in Postgres");
    }
}
