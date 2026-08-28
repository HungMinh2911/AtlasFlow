package com.atlasflow.job.execution.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record JobExcution(
        UUID id,
        UUID jobId,
        int attempt,
        JobExecutionStatus status,
        String workerId,
        LocalDateTime startAt,
        LocalDateTime finishedAt

) {
    public static JobExcution create (
            UUID jobId,
            int attempt
    ){
        String workerId = "local_worker_1";
        return new JobExcution(
                UUID.randomUUID(),
                jobId,
                attempt,
                JobExecutionStatus.RUNNING,
                workerId,
                LocalDateTime.now(),
                null
        );
    }
    public JobExcution finish (JobExecutionStatus status){
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

        return new JobExcution(
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
