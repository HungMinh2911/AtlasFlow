package com.atlasflow.job.execution.application.port;

import com.atlasflow.job.execution.domain.JobExecution;

import java.util.UUID;
import java.util.Optional;



public interface JobExecutionRepository {

    Optional<JobExecution> findById(UUID executionId);

    int max_attempt(UUID id);
    JobExecution save (JobExecution jobExcution);
}
