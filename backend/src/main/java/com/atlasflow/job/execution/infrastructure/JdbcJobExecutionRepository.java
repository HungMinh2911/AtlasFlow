package com.atlasflow.job.execution.infrastructure;

import com.atlasflow.job.execution.application.port.JobExecutionRepository;
import com.atlasflow.job.execution.domain.JobExecution;
import com.atlasflow.job.execution.domain.JobExecutionStatus;
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
public class JdbcJobExecutionRepository implements JobExecutionRepository {

    private final JdbcClient jdbcClient;

    public JdbcJobExecutionRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    private JobExecution mapRow(ResultSet rs, int rowNum) throws SQLException {
        Timestamp startedAtTs = rs.getTimestamp("started_at");
        Timestamp finishedAtTs = rs.getTimestamp("finished_at");

        return new JobExecution(
                rs.getObject("id", UUID.class),
                rs.getObject("job_id", UUID.class),
                rs.getInt("attempt"),
                JobExecutionStatus.valueOf(rs.getString("status")),
                rs.getString("worker_id"),
                startedAtTs != null 
                        ? startedAtTs.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime() 
                        : null,
                finishedAtTs != null 
                        ? finishedAtTs.toInstant().atZone(ZoneOffset.UTC).toLocalDateTime() 
                        : null
        );
    }

    @Override
    public Optional<JobExecution> findById(UUID executionId) {
        String sql = """
            SELECT id, job_id, attempt, status, worker_id, started_at, finished_at
            FROM job_executions
            WHERE id = :id
        """;

        return jdbcClient.sql(sql)
                .param("id", executionId)
                .query(this::mapRow)
                .optional();
    }

    @Override
    public int max_attempt(UUID jobId) {
        String sql = """
            SELECT COALESCE(MAX(attempt), 0) + 1 
            FROM job_executions 
            WHERE job_id = :jobId
        """;

        Integer nextAttempt = jdbcClient.sql(sql)
                .param("jobId", jobId)
                .query(Integer.class)
                .single();

        return nextAttempt != null ? nextAttempt : 1;
    }

    @Override
    public JobExecution save(JobExecution jobExecution) {
        String sql = """
            INSERT INTO job_executions (id, job_id, attempt, status, worker_id, started_at, finished_at)
            VALUES (:id, :jobId, :attempt, :status, :workerId, :startedAt, :finishedAt)
            ON CONFLICT (id) DO UPDATE
                SET status = EXCLUDED.status,
                    finished_at = EXCLUDED.finished_at
        """;

        jdbcClient.sql(sql)
                .param("id", jobExecution.id())
                .param("jobId", jobExecution.jobId())
                .param("attempt", jobExecution.attempt())
                .param("status", jobExecution.status().name())
                .param("workerId", jobExecution.workerId())
                .param("startedAt", jobExecution.startAt() != null ? Timestamp.valueOf(jobExecution.startAt()) : null)
                .param("finishedAt", jobExecution.finishedAt() != null ? Timestamp.valueOf(jobExecution.finishedAt()) : null)
                .update();

        return jobExecution;
    }
}
