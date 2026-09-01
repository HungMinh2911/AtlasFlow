package com.atlasflow.outbox_events.application;

import com.atlasflow.outbox_events.application.port.OutboxEventsRepository;
import com.atlasflow.outbox_events.domain.OutboxEvents;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.UUID;

@Service
public class OutboxEventsApplicationService {

    private final OutboxEventsRepository outboxEventsRepository;
    private final ObjectMapper objectMapper;

    public OutboxEventsApplicationService(OutboxEventsRepository outboxEventsRepository, ObjectMapper objectMapper) {
        this.outboxEventsRepository = outboxEventsRepository;
        this.objectMapper = objectMapper;
    }

    public OutboxEvents createOutboxEvents(
            String aggregate_type,
            UUID aggregate_id,
            String event_type,
            JsonNode payload
    ) {
        OutboxEvents outboxEvents = OutboxEvents.create(
                aggregate_type,
                aggregate_id,
                event_type,
                payload
        );

        return outboxEventsRepository.save(outboxEvents);
    }

    public List<OutboxEvents> findUnpublishedEvents(int limit) {
        return outboxEventsRepository.findUnpublishedEvents(limit);
    }

    public void markAsPublished(UUID id) {
        outboxEventsRepository.markAsPublished(id);
    }

    public JsonNode createPayload(UUID jobId, UUID projectId, UUID workflowId, UUID fileId) {
        ObjectNode objectNode = objectMapper.createObjectNode();

        objectNode.put("job_id", String.valueOf(jobId));
        objectNode.put("project_id", String.valueOf(projectId));
        objectNode.put("workflow_id", String.valueOf(workflowId));
        objectNode.put("file_id", String.valueOf(fileId));

        return objectNode;
    }
}
