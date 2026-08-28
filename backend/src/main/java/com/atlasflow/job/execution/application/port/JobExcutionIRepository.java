package com.atlasflow.job.execution.application.port;

import com.atlasflow.job.execution.domain.JobExcution;

import java.util.UUID;
import java.util.Optional;



public interface JobExcutionIRepository {

    Optional<JobExcution> findById(UUID executionId);

    int max_attempt(UUID id);
    JobExcution save (JobExcution jobExcution);
}
