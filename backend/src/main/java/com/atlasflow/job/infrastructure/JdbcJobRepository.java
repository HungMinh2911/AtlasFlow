package com.atlasflow.job.infrastructure;

import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobStatus;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@Primary
public class JdbcJobRepository implements JobRepository {

    private final JdbcClient jdbcClient;

    public JdbcJobRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private Job mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp createdAtTs = rs.getTimestamp("created_at");

        return new Job(
                rs.getObject("id", UUID.class),
                rs.getObject("project_id", UUID.class),
                rs.getObject("workflow_id", UUID.class),
                rs.getObject("file_id", UUID.class),
                JobStatus.valueOf(rs.getString("status")),
                rs.getString("idempotency_key"),
                rs.getString("request_fingerprint"),
                createdAtTs != null 
                        ? createdAtTs.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime() 
                        : null
        );
    }

    @Override
    public Optional<Job> findById(UUID id) {
        String sql = """
            SELECT id, project_id, workflow_id, file_id, status, 
                   idempotency_key, request_fingerprint, created_at
            FROM jobs
            WHERE id = :id
        """;

        return jdbcClient.sql(sql)
                .param("id", id)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<Job> findByIdempotencyKey(String idempotencyKey) {
        String sql = """
            SELECT id, project_id, workflow_id, file_id, status, 
                   idempotency_key, request_fingerprint, created_at
            FROM jobs
            WHERE idempotency_key = :idempotencyKey
        """;

        return jdbcClient.sql(sql)
                .param("idempotencyKey", idempotencyKey)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<Job> findByProjectIdAndIdempotencyKey(UUID projectId, String idempotencyKey) {
        String sql = """
            SELECT id, project_id, workflow_id, file_id, status, 
                   idempotency_key, request_fingerprint, created_at
            FROM jobs
            WHERE project_id = :projectId AND idempotency_key = :idempotencyKey
        """;

        return jdbcClient.sql(sql)
                .param("projectId", projectId)
                .param("idempotencyKey", idempotencyKey)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public Optional<Job> insertIfAbsent(Job job) {
        String sql = """
            INSERT INTO jobs (id, project_id, workflow_id, file_id, status, 
                              idempotency_key, request_fingerprint, created_at)
            VALUES (:id, :projectId, :workflowId, :fileId, :status, 
                    :idempotencyKey, :requestFingerprint, :createdAt)
            ON CONFLICT (project_id, idempotency_key) DO NOTHING
        """;

        int rows = jdbcClient.sql(sql)
                .param("id", job.id())
                .param("projectId", job.projectId())
                .param("workflowId", job.workflowId())
                .param("fileId", job.fileId())
                .param("status", job.status().name())
                .param("idempotencyKey", job.idempotencyKey())
                .param("requestFingerprint", job.requestFingerprint())
                .param("createdAt", job.createdAt() != null ? Timestamp.valueOf(job.createdAt()) : null)
                .update();

        return rows > 0 ? Optional.of(job) : Optional.empty();
    }

    @Override
    public Job save(Job job) {
        String sql = """
            INSERT INTO jobs (id, project_id, workflow_id, file_id, status, 
                              idempotency_key, request_fingerprint, created_at)
            VALUES (:id, :projectId, :workflowId, :fileId, :status, 
                    :idempotencyKey, :requestFingerprint, :createdAt)
            ON CONFLICT (id) DO UPDATE
                SET status = EXCLUDED.status
        """;

        jdbcClient.sql(sql)
                .param("id", job.id())
                .param("projectId", job.projectId())
                .param("workflowId", job.workflowId())
                .param("fileId", job.fileId())
                .param("status", job.status().name())
                .param("idempotencyKey", job.idempotencyKey())
                .param("requestFingerprint", job.requestFingerprint())
                .param("createdAt", job.createdAt() != null ? Timestamp.valueOf(job.createdAt()) : null)
                .update();

        return job;
    }

    @Override
    public int  updateStatusToRunning(UUID jobId) {
        String sql = """
        UPDATE jobs
        SET status = 'RUNNING'
        WHERE id = :jobId
          AND status = 'QUEUED'
        """;

         return jdbcClient.sql(sql)
                .param("jobId", jobId)
                .update();
    }


}
