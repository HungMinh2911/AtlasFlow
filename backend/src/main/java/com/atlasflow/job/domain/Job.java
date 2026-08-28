package com.atlasflow.job.domain;

import java.time.LocalDateTime;
import java.util.UUID;

public record Job(
        UUID id,
        UUID projectId,
        UUID workflowId,
        UUID fileId,
        JobStatus status,
        String idempotencyKey,
        LocalDateTime createdAt
) {
    public static Job submit(
            UUID id,
            UUID projectId,
            UUID workflowId,
            UUID fileId,
            String idempotencyKey
    ) {
        return new Job(
                id,
                projectId,
                workflowId,
                fileId,
                JobStatus.SUBMITTED,
                idempotencyKey,
                LocalDateTime.now()
        );
    }
    public Job markQueued() {
        if (status != JobStatus.SUBMITTED) {
            throw new IllegalStateException(
                    "Only SUBMITTED job can be queued"
            );
        }

        return new Job(
                id,
                projectId,
                workflowId,
                fileId,
                JobStatus.QUEUED,
                idempotencyKey,
                createdAt
        );
    }
    public Job startRun() {
        if (status != JobStatus.QUEUED) {
            throw new IllegalStateException(
                    "Only QUEUED job can start running"
            );
        }

        return new Job(
                id,
                projectId,
                workflowId,
                fileId,
                JobStatus.RUNNING,
                idempotencyKey,
                createdAt
        );
    }

    public Job finish(JobStatus finalStatus) {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING job can be finished"
            );
        }

        if (finalStatus != JobStatus.SUCCEEDED
                && finalStatus != JobStatus.FAILED
                && finalStatus != JobStatus.CANCELLED) {
            throw new IllegalArgumentException(
                    "Invalid terminal job status: " + finalStatus
            );
        }

        return new Job(
                id,
                projectId,
                workflowId,
                fileId,
                finalStatus,
                idempotencyKey,
                createdAt
        );
    }
}
