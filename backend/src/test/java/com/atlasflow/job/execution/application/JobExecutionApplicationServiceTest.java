package com.atlasflow.job.execution.application;

import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobStatus;
import com.atlasflow.job.domain.JobValidationException;
import com.atlasflow.job.execution.api.CreateJobExecutionRequest;
import com.atlasflow.job.execution.api.JobExecutionResponse;
import com.atlasflow.job.execution.application.port.JobExecutionRepository;
import com.atlasflow.job.execution.domain.JobExecution;
import com.atlasflow.job.execution.domain.JobExecutionNotFoundException;
import com.atlasflow.job.execution.domain.JobExecutionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobExecutionApplicationServiceTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobExecutionRepository executionRepository;

    private JobExecutionApplicationService service;

    @BeforeEach
    void setUp() {
        service = new JobExecutionApplicationService(
                jobRepository,
                executionRepository
        );
    }

    @Test
    void createJobExecutionShouldCreateRunningExecutionAndStartJob() {
        UUID jobId = UUID.randomUUID();
        Job queuedJob = createJob(jobId, JobStatus.QUEUED);

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(queuedJob));
        when(executionRepository.max_attempt(jobId))
                .thenReturn(1);
        when(jobRepository.updateStatusToRunning(jobId))
                .thenReturn(1);
        when(executionRepository.saveIfAbsent(any(JobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobExecutionResponse response = service.createJobExecution(
                new CreateJobExecutionRequest(jobId)
        );

        assertEquals(jobId, response.jobId());
        assertEquals(1, response.attempt());
        assertEquals(JobExecutionStatus.RUNNING, response.status());
        assertEquals("local_worker_1", response.workerId());

        verify(jobRepository).updateStatusToRunning(jobId);

        ArgumentCaptor<JobExecution> executionCaptor =
                ArgumentCaptor.forClass(JobExecution.class);
        verify(executionRepository).saveIfAbsent(executionCaptor.capture());
        assertEquals(jobId, executionCaptor.getValue().jobId());
        assertEquals(JobExecutionStatus.RUNNING,
                executionCaptor.getValue().status());
    }

    @Test
    void createJobExecutionShouldRejectUnknownJob() {
        UUID jobId = UUID.randomUUID();

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.empty());

        assertThrows(
                RuntimeException.class,
                () -> service.createJobExecution(
                        new CreateJobExecutionRequest(jobId)
                )
        );
    }

    @Test
    void createJobExecutionShouldRejectJobThatIsNotQueued() {
        UUID jobId = UUID.randomUUID();
        Job submittedJob = createJob(jobId, JobStatus.SUBMITTED);

        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(submittedJob));

        assertThrows(
                JobValidationException.class,
                () -> service.createJobExecution(
                        new CreateJobExecutionRequest(jobId)
                )
        );
    }

    @Test
    void finishExecutionShouldMarkExecutionAndJobAsSucceeded() {
        UUID jobId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Job runningJob = createJob(jobId, JobStatus.RUNNING);
        JobExecution runningExecution = createExecution(
                executionId,
                jobId,
                JobExecutionStatus.RUNNING
        );

        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(runningExecution));
        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(runningJob));
        when(executionRepository.save(any(JobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        JobExecutionResponse response = service.finishExecution(
                executionId,
                JobExecutionStatus.SUCCEEDED
        );

        assertEquals(JobExecutionStatus.SUCCEEDED, response.status());
        verifySavedStates(JobStatus.SUCCEEDED,
                JobExecutionStatus.SUCCEEDED);
    }

    @ParameterizedTest
    @EnumSource(value = JobExecutionStatus.class,
            names = {"SUCCEEDED", "FAILED", "CANCELLED"})
    void finishExecutionShouldMapTerminalExecutionStatusToJobStatus(
            JobExecutionStatus executionStatus
    ) {
        UUID jobId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Job runningJob = createJob(jobId, JobStatus.RUNNING);
        JobExecution runningExecution = createExecution(
                executionId,
                jobId,
                JobExecutionStatus.RUNNING
        );

        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(runningExecution));
        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(runningJob));
        when(executionRepository.save(any(JobExecution.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jobRepository.save(any(Job.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.finishExecution(executionId, executionStatus);

        JobStatus expectedJobStatus = switch (executionStatus) {
            case SUCCEEDED -> JobStatus.SUCCEEDED;
            case FAILED -> JobStatus.FAILED;
            case CANCELLED -> JobStatus.CANCELLED;
            case RUNNING -> throw new AssertionError();
        };

        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertEquals(expectedJobStatus, jobCaptor.getValue().status());
    }

    @Test
    void finishExecutionShouldRejectUnknownExecution() {
        UUID executionId = UUID.randomUUID();

        when(executionRepository.findById(executionId))
                .thenReturn(Optional.empty());

        assertThrows(
                JobExecutionNotFoundException.class,
                () -> service.finishExecution(
                        executionId,
                        JobExecutionStatus.SUCCEEDED
                )
        );
    }

    @Test
    void finishExecutionShouldRejectWhenJobIsNotRunning() {
        UUID jobId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Job queuedJob = createJob(jobId, JobStatus.QUEUED);
        JobExecution runningExecution = createExecution(
                executionId,
                jobId,
                JobExecutionStatus.RUNNING
        );

        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(runningExecution));
        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(queuedJob));

        assertThrows(
                IllegalStateException.class,
                () -> service.finishExecution(
                        executionId,
                        JobExecutionStatus.SUCCEEDED
                )
        );
    }

    @Test
    void finishExecutionShouldRejectRunningAsFinalStatus() {
        UUID jobId = UUID.randomUUID();
        UUID executionId = UUID.randomUUID();
        Job runningJob = createJob(jobId, JobStatus.RUNNING);
        JobExecution runningExecution = createExecution(
                executionId,
                jobId,
                JobExecutionStatus.RUNNING
        );

        when(executionRepository.findById(executionId))
                .thenReturn(Optional.of(runningExecution));
        when(jobRepository.findById(jobId))
                .thenReturn(Optional.of(runningJob));

        assertThrows(
                JobValidationException.class,
                () -> service.finishExecution(
                        executionId,
                        JobExecutionStatus.RUNNING
                )
        );
    }

    private void verifySavedStates(
            JobStatus expectedJobStatus,
            JobExecutionStatus expectedExecutionStatus
    ) {
        ArgumentCaptor<Job> jobCaptor = ArgumentCaptor.forClass(Job.class);
        verify(jobRepository).save(jobCaptor.capture());
        assertEquals(expectedJobStatus, jobCaptor.getValue().status());

        ArgumentCaptor<JobExecution> executionCaptor =
                ArgumentCaptor.forClass(JobExecution.class);
        verify(executionRepository).save(executionCaptor.capture());
        assertEquals(expectedExecutionStatus,
                executionCaptor.getValue().status());
    }

    private Job createJob(UUID jobId, JobStatus status) {
        return new Job(
                jobId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                status,
                "idempotency-key-" + jobId,
                LocalDateTime.now()
        );
    }

    private JobExecution createExecution(
            UUID executionId,
            UUID jobId,
            JobExecutionStatus status
    ) {
        return new JobExecution(
                executionId,
                jobId,
                1,
                status,
                "local_worker_1",
                LocalDateTime.now(),
                null
        );
    }
}
