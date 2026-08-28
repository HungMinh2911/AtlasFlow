package com.atlasflow.job.api;

import com.atlasflow.job.domain.Job;
import java.time.LocalDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id,
        UUID project_id,
        UUID workflow_id,
        UUID file_id,
        String status,
        String idempotency_key,
        LocalDateTime created_at
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.id(),
                job.projectId(),
                job.workflowId(),
                job.fileId(),
                job.status().name(),
                job.idempotencyKey(),
                job.createdAt()
        );
    }
}
