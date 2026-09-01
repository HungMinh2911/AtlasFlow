package com.atlasflow.outbox_events.domain;

import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.UUID;

public record OutboxEvents(
        UUID id,
        String aggregate_type,
        UUID aggregate_id,
        String event_type,
        JsonNode payload,
        LocalDateTime published_at
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
                null
        );
    }
}
