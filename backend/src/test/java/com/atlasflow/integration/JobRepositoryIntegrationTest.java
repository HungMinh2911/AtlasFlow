package com.atlasflow.integration;

import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobRepositoryIntegrationTest extends AbstractBaseIntegrationTest {

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldSaveAndFindJobByIdempotencyKeyInTestcontainerPostgres() {
        UUID orgId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID workflowId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID jobId = UUID.randomUUID();
        String idempotencyKey = "key-tc-100";

        jdbcTemplate.update("INSERT INTO organizations (id, name) VALUES (?, ?)", orgId, "Job Org TC");
        jdbcTemplate.update("INSERT INTO projects (id, organization_id, name, status) VALUES (?, ?, ?, 'ACTIVE')", projectId, orgId, "Job Proj TC");
        jdbcTemplate.update("INSERT INTO workflows (id, project_id, version, status) VALUES (?, ?, 1, 'PUBLISHED')", workflowId, projectId);
        jdbcTemplate.update("INSERT INTO files (id, project_id, file_name, object_key, checksum, size_bytes, status) VALUES (?, ?, 'in.txt', ?, 'sum', 10, 'AVAILABLE')", fileId, projectId, "key/jobrepo-" + UUID.randomUUID());

        Job job = Job.submit(jobId, projectId, workflowId, fileId, idempotencyKey, "fp-123");
        jobRepository.save(job);

        Optional<Job> found = jobRepository.findByProjectIdAndIdempotencyKey(projectId, idempotencyKey);

        assertTrue(found.isPresent());
        assertEquals(jobId, found.get().id());
        assertEquals(JobStatus.SUBMITTED, found.get().status());
    }
}
