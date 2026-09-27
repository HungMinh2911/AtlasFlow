package com.atlasflow.job.execution.infrastructure;

import com.atlasflow.job.execution.domain.JobExecution;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryJobExecutionRepositoryTest {

    private final InMemoryJobExecutionRepository repository =
            new InMemoryJobExecutionRepository();

    @Test
    void saveAndFindByIdShouldReturnExecution() {
        JobExecution execution = JobExecution.create(UUID.randomUUID(), 1);

        repository.save(execution);

        assertEquals(
                execution,
                repository.findById(execution.id()).orElseThrow()
        );
    }

    @Test
    void maxAttemptShouldReturnOneWhenJobHasNoExecution() {
        UUID jobId = UUID.randomUUID();

        assertEquals(1, repository.max_attempt(jobId));
    }

    @Test
    void maxAttemptShouldReturnNextAttemptForSameJob() {
        UUID jobId = UUID.randomUUID();

        repository.save(JobExecution.create(jobId, 1));
        repository.save(new JobExecution(
                UUID.randomUUID(),
                jobId,
                2,
                com.atlasflow.job.execution.domain.JobExecutionStatus.FAILED,
                "local_worker_1",
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        ));

        assertEquals(3, repository.max_attempt(jobId));
    }

    @Test
    void maxAttemptShouldIgnoreExecutionOfAnotherJob() {
        UUID firstJobId = UUID.randomUUID();
        UUID secondJobId = UUID.randomUUID();

        repository.save(JobExecution.create(firstJobId, 1));
        repository.save(JobExecution.create(secondJobId, 1));
        repository.save(new JobExecution(
                UUID.randomUUID(),
                secondJobId,
                2,
                com.atlasflow.job.execution.domain.JobExecutionStatus.FAILED,
                "local_worker_1",
                java.time.LocalDateTime.now(),
                java.time.LocalDateTime.now()
        ));

        assertEquals(2, repository.max_attempt(firstJobId));
        assertTrue(repository.findById(UUID.randomUUID()).isEmpty());
    }
}
