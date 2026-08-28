package com.atlasflow.job.execution.api;

import com.atlasflow.job.execution.domain.JobExcution;
import com.atlasflow.job.execution.domain.JobExecutionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobExcutionRespone(
        UUID id,
        UUID jobId,
        int attempt,
        JobExecutionStatus status,
        String workerId,
        LocalDateTime startAt,
        LocalDateTime finishedAt
) {

    public static JobExcutionRespone from (JobExcution jobExcution){
        return  new JobExcutionRespone(
                jobExcution.id(),
                jobExcution.jobId(),
                jobExcution.attempt(),
                jobExcution.status(),
                jobExcution.workerId(),
                jobExcution.startAt(),
                jobExcution.finishedAt()
        );
    }
}
