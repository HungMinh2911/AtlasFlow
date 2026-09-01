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

import java.util.UUID;

/**
 * Nơi xử lý các use case liên quan đến JobExecution.
 */
@Service
public class JobExecutionApplicationService {

    private JobRepository jobRepository;
    private JobExecutionRepository jobExcutionIRepository;

    public JobExecutionApplicationService(
            JobRepository jobRepository,
            JobExecutionRepository jobExcutionIRepository
    ) {
        this.jobRepository = jobRepository;
        this.jobExcutionIRepository = jobExcutionIRepository;
    }

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

        int attempt = jobExcutionIRepository.max_attempt(jobId);
        JobExecution execution = JobExecution.create(jobId, attempt);

        jobRepository.save(job.startRun());
        jobExcutionIRepository.save(execution);

        return JobExecutionResponse.from(execution);
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
