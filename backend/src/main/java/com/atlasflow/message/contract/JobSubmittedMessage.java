package com.atlasflow.message.contract;

import java.time.Instant;
import java.util.UUID;

public record JobSubmittedMessage(
        UUID messageId,
        String eventType,
        int eventVersion,
        Instant occurredAt,
        String correlationId,
        String aggregateType,
        UUID aggregateId,
        JobSubmittedPayload payload
) {
}
