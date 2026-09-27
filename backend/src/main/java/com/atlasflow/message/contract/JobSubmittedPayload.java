package com.atlasflow.message.contract;

import java.util.UUID;

public record JobSubmittedPayload(
        UUID jobId,
        UUID projectId,
        UUID workflowId,
        UUID fileId,
        String idempotencyKey
) {
}
