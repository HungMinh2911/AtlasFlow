package com.atlasflow.job.execution.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobExecution(
        UUID id,
        UUID jobId,
        int attempt,
        JobExecutionStatus status,
        String workerId,
        LocalDateTime startAt,
        LocalDateTime finishedAt

) {
    public static JobExecution create (
            UUID jobId,
            int attempt
    ){
        String workerId = "local_worker_1";
        return new JobExecution(
                UUID.randomUUID(),
                jobId,
                attempt,
                JobExecutionStatus.RUNNING,
                workerId,
                LocalDateTime.now(),
                null
        );
    }
    public JobExecution finish (JobExecutionStatus status){
        if (this.status != JobExecutionStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING execution can be finished"
            );
        }

        if (status != JobExecutionStatus.SUCCEEDED
                && status != JobExecutionStatus.FAILED
                && status != JobExecutionStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Invalid terminal execution status: " + status
            );
        }

        return new JobExecution(
                id,
                jobId,
                attempt,
                status,
                workerId,
                startAt,
                LocalDateTime.now()
        );
    }
}
