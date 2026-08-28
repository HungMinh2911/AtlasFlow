package com.atlasflow.job.execution.application;

import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobNotFoundException;
import com.atlasflow.job.domain.JobStatus;
import com.atlasflow.job.domain.JobValidationException;
import com.atlasflow.job.execution.api.CreateJobExcutionRequest;
import com.atlasflow.job.execution.api.JobExcutionRespone;
import com.atlasflow.job.execution.application.port.JobExcutionIRepository;
import com.atlasflow.job.execution.domain.JobExcution;
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
    private JobExcutionIRepository jobExcutionIRepository;

    public JobExecutionApplicationService(
            JobRepository jobRepository,
            JobExcutionIRepository jobExcutionIRepository
    ) {
        this.jobRepository = jobRepository;
        this.jobExcutionIRepository = jobExcutionIRepository;
    }

    public JobExcutionRespone createJobExecution(
            CreateJobExcutionRequest request
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
        JobExcution execution = JobExcution.create(jobId, attempt);

        jobRepository.save(job.startRun());
        jobExcutionIRepository.save(execution);

        return JobExcutionRespone.from(execution);
    }

    public JobExcutionRespone finishExecution(
            UUID executionId,
            JobExecutionStatus executionStatus
    ) {
        JobExcution execution = jobExcutionIRepository.findById(executionId)
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

        JobExcution finishedExecution = execution.finish(executionStatus);
        Job finishedJob = job.finish(finalJobStatus);

        jobExcutionIRepository.save(finishedExecution);
        jobRepository.save(finishedJob);

        return JobExcutionRespone.from(finishedExecution);
    }
}
