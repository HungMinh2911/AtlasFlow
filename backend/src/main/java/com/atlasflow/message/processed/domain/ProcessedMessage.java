package com.atlasflow.message.processed.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Records that a message was successfully processed by a consumer.
 *
 * <p>The actual duplicate check is enforced by the database primary key on
 * {@code message_id}; this record intentionally contains no business logic.</p>
 */
public record ProcessedMessage(
        UUID messageId,
        String consumerName,
        UUID aggregateId,
        Instant processedAt
) {
}
