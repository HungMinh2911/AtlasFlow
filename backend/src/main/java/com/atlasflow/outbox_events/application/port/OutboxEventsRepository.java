package com.atlasflow.outbox_events.application.port;

import com.atlasflow.outbox_events.domain.OutboxEvents;

import java.util.List;
import java.util.UUID;

public interface OutboxEventsRepository {

    OutboxEvents save(OutboxEvents events);

    List<OutboxEvents> findUnpublishedEvents(int limit);

    void markAsPublished(UUID id);
}
