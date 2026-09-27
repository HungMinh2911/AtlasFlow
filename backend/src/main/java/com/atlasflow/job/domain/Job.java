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
        String requestFingerprint,
        LocalDateTime createdAt
) {
    public Job(UUID id, UUID projectId, UUID workflowId, UUID fileId,
               JobStatus status, String idempotencyKey, LocalDateTime createdAt) {
        this(id, projectId, workflowId, fileId, status, idempotencyKey, null, createdAt);
    }

    public static Job submit(
            UUID id,
            UUID projectId,
            UUID workflowId,
            UUID fileId,
            String idempotencyKey,
            String requestFingerprint
    ) {
        return new Job(
                id,
                projectId,
                workflowId,
                fileId,
                JobStatus.SUBMITTED,
                idempotencyKey,
                requestFingerprint,
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
                requestFingerprint,
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
                requestFingerprint,
                createdAt
        );
    }

    public Job requeueForRetry() {
        if (status != JobStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only RUNNING job can be requeued for retry"
            );
        }

        return new Job(
                id,
                projectId,
                workflowId,
                fileId,
                JobStatus.QUEUED,
                idempotencyKey,
                requestFingerprint,
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
                requestFingerprint,
                createdAt
        );
    }
}
