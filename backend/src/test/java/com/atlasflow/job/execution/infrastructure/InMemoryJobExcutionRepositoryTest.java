package com.atlasflow.job.execution.infrastructure;

import com.atlasflow.job.execution.domain.JobExcution;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryJobExcutionRepositoryTest {

    private final InMemoryJobExcutionRepository repository =
            new InMemoryJobExcutionRepository();

    @Test
    void saveAndFindByIdShouldReturnExecution() {
        JobExcution execution = JobExcution.create(UUID.randomUUID(), 1);

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

        repository.save(JobExcution.create(jobId, 1));
        repository.save(new JobExcution(
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

        repository.save(JobExcution.create(firstJobId, 1));
        repository.save(JobExcution.create(secondJobId, 1));
        repository.save(new JobExcution(
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
