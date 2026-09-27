package com.atlasflow.outbox_events.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxEvents(
        UUID id,
        String aggregate_type,
        UUID aggregate_id,
        String event_type,
        JsonNode payload,
        LocalDateTime occurred_at,
        String correlation_id,
        LocalDateTime published_at,
        int delivery_attempts,
        String last_error
) {
    public static OutboxEvents create (
            String aggregate_type,
            UUID aggregate_id,
            String event_type,
            JsonNode payload
    ){
        return new OutboxEvents(
                UUID.randomUUID(),
                aggregate_type,
                aggregate_id,
                event_type,
                payload,
                LocalDateTime.now(),
                UUID.randomUUID().toString(),
                null,
                0,
                null
        );
    }
}
