package com.atlasflow.job.application;

import com.atlasflow.job.application.port.JobRepository;
import com.atlasflow.job.domain.Job;
import com.atlasflow.job.domain.JobStatus;
import com.atlasflow.job.execution.api.CreateJobExecutionRequest;
import com.atlasflow.job.execution.api.JobExecutionResponse;
import com.atlasflow.job.execution.application.JobExecutionApplicationService;
import com.atlasflow.job.execution.application.port.JobExecutionRepository;
import com.atlasflow.job.execution.domain.JobExecution;
import com.atlasflow.job.execution.domain.JobExecutionStatus;
import com.atlasflow.message.contract.JobSubmittedMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class JobSubmittedHandler {

    private final JobRepository repository;
    private final JobExecutionApplicationService jobExecutionApplicationService;
    private final JobExecutionRepository jobExecutionRepository;
    public JobSubmittedHandler(
            JobRepository repository,
            JobExecutionApplicationService jobExecutionApplicationService,
            JobExecutionRepository jobExecutionRepository
    ) {
        this.repository = repository;
        this.jobExecutionApplicationService = jobExecutionApplicationService;
        this.jobExecutionRepository = jobExecutionRepository;
    }

    @Transactional
    public Optional<JobExecutionResponse> handle(JobSubmittedMessage message) {
        Job job = repository.findById(message.payload().jobId())
                .orElseThrow(() -> new RuntimeException("Job not found: " + message.payload().jobId()));

        if (job.status() == JobStatus.SUBMITTED) {
            Job queuedJob = job.markQueued();
            repository.save(queuedJob);

            return Optional.of(jobExecutionApplicationService.createJobExecution(
                    new CreateJobExecutionRequest(queuedJob.id())
            ));
        } else if (job.status() == JobStatus.QUEUED) {

            int updatedRows = repository.updateStatusToRunning(job.id());

            if (updatedRows != 1) {
                return Optional.empty();
            }

            Optional<JobExecution> latestExecution =
                    jobExecutionRepository.findLatestByJobId(job.id());

            if (latestExecution.isEmpty()
                    || latestExecution.get().status() == JobExecutionStatus.FAILED) {
                return Optional.of(
                        jobExecutionApplicationService
                                .createExecutionForRunningJob(job.id())
                );
            }

        } else if (job.status() == JobStatus.RUNNING)  {
            // A duplicate delivery must not turn a currently running job
            // into a retry. Recovery of stale RUNNING jobs belongs to a
            // separate recovery process.
            return Optional.empty();
        }


        return Optional.empty();
    }



}
