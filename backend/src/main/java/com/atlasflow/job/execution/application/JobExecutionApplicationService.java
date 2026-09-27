package com.atlasflow.job.execution.application;

import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobNotFoundException;
import com.atlasflow.job.domain.JobStatus;
import com.atlasflow.job.domain.JobValidationException;
import com.atlasflow.job.execution.api.CreateJobExecutionRequest;
import com.atlasflow.job.execution.api.JobExecutionResponse;
import com.atlasflow.job.execution.application.port.JobExecutionRepository;
import com.atlasflow.job.execution.domain.JobExecution;
import com.atlasflow.job.execution.domain.JobExecutionNotFoundException;
import com.atlasflow.job.execution.domain.JobExecutionStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Nơi xử lý các use case liên quan đến JobExecution.
 */
@Service
public class JobExecutionApplicationService {

    private  final JobRepository jobRepository;
    private final JobExecutionRepository jobExcutionIRepository;

    public JobExecutionApplicationService(
            JobRepository jobRepository,
            JobExecutionRepository jobExcutionIRepository
    ) {
        this.jobRepository = jobRepository;
        this.jobExcutionIRepository = jobExcutionIRepository;
    }

    @Transactional
    public JobExecutionResponse createJobExecution(
            CreateJobExecutionRequest request
    ) {

        UUID jobId = request.jobId();
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.status() != JobStatus.QUEUED) {
            throw new JobValidationException(
                    "Only QUEUED job can create an execution"
            );
        }

        // Atomically claim the job. Only one consumer can move it from
        // QUEUED to RUNNING; concurrent consumers get 0 affected rows.
        int updatedRows = jobRepository.updateStatusToRunning(jobId);
        if (updatedRows != 1) {
            throw new JobValidationException(
                    "Job is no longer QUEUED or was claimed by another process"
            );
        }

        return createExecutionForRunningJob(jobId);
    }

    /**
     * Tạo execution cho job vừa được claim thành công.
     * Caller bắt buộc phải kiểm tra updateStatusToRunning(...) == 1 trước
     * khi gọi hàm này. Hàm này không claim và không kiểm tra QUEUED lần nữa.
     */
    @Transactional
    public JobExecutionResponse createExecutionForRunningJob(UUID jobId) {
        int attempt = jobExcutionIRepository.max_attempt(jobId);
        JobExecution execution = JobExecution.create(jobId, attempt);

        Optional<JobExecution> existingExecution =
                jobExcutionIRepository.findByJobIdAndAttempt(jobId, attempt);
        if (existingExecution.isPresent()) {
            return JobExecutionResponse.from(existingExecution.get());
        }

        JobExecution savedExecution =
                jobExcutionIRepository.saveIfAbsent(execution);

        return JobExecutionResponse.from(savedExecution);
    }

    @Transactional
    public JobExecutionResponse retryRunningExecution(UUID jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        if (job.status() != JobStatus.RUNNING) {
            throw new JobValidationException(
                    "Only RUNNING job can retry"
            );
        }

        JobExecution currentExecution = jobExcutionIRepository
                .findLatestByJobId(jobId)
                .orElseThrow(() -> new JobExecutionNotFoundException(jobId));

        if (currentExecution.status() == JobExecutionStatus.RUNNING) {
            jobExcutionIRepository.save(
                    currentExecution.finish(JobExecutionStatus.FAILED)
            );
        }

        jobRepository.save(job.requeueForRetry());

        return createJobExecution(new CreateJobExecutionRequest(jobId));
    }

    /** Marks the current attempt failed and makes the job eligible for retry. */
    @Transactional
    public void failAndRequeue(UUID executionId) {
        JobExecution execution = jobExcutionIRepository.findById(executionId)
                .orElseThrow(() -> new JobExecutionNotFoundException(executionId));
        Job job = jobRepository.findById(execution.jobId())
                .orElseThrow(() -> new JobNotFoundException(execution.jobId()));

        if (execution.status() == JobExecutionStatus.RUNNING) {
            jobExcutionIRepository.save(execution.finish(JobExecutionStatus.FAILED));
        }
        if (job.status() == JobStatus.RUNNING) {
            jobRepository.save(job.requeueForRetry());
        }
    }




    public JobExecutionResponse finishExecution(
            UUID executionId,
            JobExecutionStatus executionStatus
    ) {
        JobExecution execution = jobExcutionIRepository.findById(executionId)
                .orElseThrow(() -> new JobExecutionNotFoundException(executionId));

        Job job = jobRepository.findById(execution.jobId())
                .orElseThrow(() -> new JobNotFoundException(execution.jobId()));

        JobStatus finalJobStatus = switch (executionStatus) {
            case SUCCEEDED -> JobStatus.SUCCEEDED;
            case FAILED -> JobStatus.FAILED;
            case CANCELLED -> JobStatus.CANCELLED;
            case RUNNING -> throw new JobValidationException(
                    "Execution must finish with a terminal status"
            );
        };

        JobExecution finishedExecution = execution.finish(executionStatus);
        Job finishedJob = job.finish(finalJobStatus);

        jobExcutionIRepository.save(finishedExecution);
        jobRepository.save(finishedJob);

        return JobExecutionResponse.from(finishedExecution);
    }
}
