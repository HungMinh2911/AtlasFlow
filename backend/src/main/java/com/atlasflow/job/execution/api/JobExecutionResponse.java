package com.atlasflow.job.execution.api;

import com.atlasflow.job.execution.domain.JobExecution;
import com.atlasflow.job.execution.domain.JobExecutionStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobExecutionResponse(
        UUID id,
        UUID jobId,
        int attempt,
        JobExecutionStatus status,
        String workerId,
        LocalDateTime startAt,
        LocalDateTime finishedAt
) {

    public static JobExecutionResponse from (JobExecution jobExcution){
        return  new JobExecutionResponse(
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
