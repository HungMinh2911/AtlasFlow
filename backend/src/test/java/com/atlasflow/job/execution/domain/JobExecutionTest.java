package com.atlasflow.job.execution.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JobExecutionTest {

    @Test
    void createShouldStartExecutionAsRunning() {
        UUID jobId = UUID.randomUUID();

        JobExecution execution = JobExecution.create(jobId, 1);

        assertNotNull(execution.id());
        assertEquals(jobId, execution.jobId());
        assertEquals(1, execution.attempt());
        assertEquals(JobExecutionStatus.RUNNING, execution.status());
        assertEquals("local_worker_1", execution.workerId());
        assertNotNull(execution.startAt());
        assertNull(execution.finishedAt());
    }

    @Test
    void finishShouldSetTerminalStatusAndFinishedTime() {
        JobExecution execution = new JobExecution(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                JobExecutionStatus.RUNNING,
                "local_worker_1",
                LocalDateTime.now(),
                null
        );

        JobExecution finished = execution.finish(JobExecutionStatus.SUCCEEDED);

        assertEquals(JobExecutionStatus.SUCCEEDED, finished.status());
        assertNotNull(finished.finishedAt());
        assertEquals(execution.id(), finished.id());
        assertEquals(execution.jobId(), finished.jobId());
    }

    @Test
    void finishShouldRejectAlreadyFinishedExecution() {
        JobExecution execution = new JobExecution(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                JobExecutionStatus.SUCCEEDED,
                "local_worker_1",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        assertThrows(
                IllegalStateException.class,
                () -> execution.finish(JobExecutionStatus.FAILED)
        );
    }

    @Test
    void finishShouldRejectRunningAsTerminalStatus() {
        JobExecution execution = new JobExecution(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1,
                JobExecutionStatus.RUNNING,
                "local_worker_1",
                LocalDateTime.now(),
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> execution.finish(JobExecutionStatus.RUNNING)
        );
    }
}
