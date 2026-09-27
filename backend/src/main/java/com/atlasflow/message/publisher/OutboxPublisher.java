package com.atlasflow.message.publisher;

import com.atlasflow.message.config.RabbitNames;
import com.atlasflow.message.contract.JobSubmittedMessage;
import com.atlasflow.message.contract.JobSubmittedPayload;
import com.atlasflow.outbox_events.application.OutboxEventsApplicationService;
import com.atlasflow.outbox_events.domain.OutboxEvents;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {

    private final OutboxEventsApplicationService outboxService;
    private  final RabbitTemplate rabbitTemplate;

    public OutboxPublisher(OutboxEventsApplicationService outboxService, RabbitTemplate rabbitTemplate) {
        this.outboxService = outboxService;
        this.rabbitTemplate = rabbitTemplate;
    }

    public int publishBatch(int batchSize){

        List<OutboxEvents> eventsList = outboxService.findUnpublishedEvents(batchSize);

        int publishedCount = 0;
        for (OutboxEvents events : eventsList) {
            boolean published = publishOne(events);
            if (published) {
                publishedCount++;
            }
        }
        return publishedCount;
    }


    private boolean publishOne(OutboxEvents events){
        if (!"JOB_SUBMITTED".equals(events.event_type())) {
            System.out.println(
                    "Unsupported outbox event type: " + events.event_type()
            );
            return false;
        }

        try {
            JobSubmittedMessage message = createMessage(events);

            CorrelationData correlationData = new CorrelationData(message.messageId().toString());


            rabbitTemplate.convertAndSend(
                    RabbitNames.EVENTS_EXCHANGE,
                    RabbitNames.JOB_SUBMITTED_ROUTING_KEY,
                    message,
                    outgoingMessage -> {
                        var properties = outgoingMessage.getMessageProperties();
                        properties.setContentType("application/json");
                        properties.setContentEncoding("UTF-8");
                        properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        properties.setMessageId(message.messageId().toString());
                        properties.setType(message.eventType());
                        properties.setCorrelationId(message.correlationId());
                        properties.setTimestamp(java.util.Date.from(message.occurredAt()));
                        return outgoingMessage;
                    },
                    correlationData
            );

            CorrelationData.Confirm confirm =
                    correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException(
                        "RabbitMQ did not confirm message: "
                                + confirm.getReason()
                );
            }

            outboxService.markAsPublished(events.id());

            System.out.println(
                    "Published outbox event: " + events.id()
            );

            return true;

        } catch (Exception e) {
            System.err.println(
                    "Failed to publish outbox event: " + events.id() + ", error: " + e.getMessage()
            );
            return false;
        }

    }
    private JobSubmittedMessage createMessage(OutboxEvents events){
        if (events.payload() == null || !events.payload().isObject()) {
            throw new IllegalArgumentException(
                    "Outbox event " + events.id() + " has no JSON object payload"
            );
        }

        JobSubmittedPayload payload = new JobSubmittedPayload(
                requiredUuid(events, "job_id"),
                requiredUuid(events, "project_id"),
                requiredUuid(events, "workflow_id"),
                requiredUuid(events, "file_id"),
                events.payload().has("idempotency_key") ? events.payload().get("idempotency_key").asText() : null
        );
        return  new JobSubmittedMessage(
                events.id(),
                events.event_type(),
                1,
                events.occurred_at().toInstant(ZoneOffset.UTC),
                events.correlation_id() != null
                        ? events.correlation_id()
                        : events.id().toString(),
                events.aggregate_type(),
                events.aggregate_id(),
                payload
        );
    }

    private UUID requiredUuid(OutboxEvents events, String fieldName) {
        var value = events.payload().get(fieldName);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException(
                    "Outbox event " + events.id() + " is missing payload field: " + fieldName
            );
        }

        try {
            return UUID.fromString(value.asText());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    "Outbox event " + events.id() + " has invalid UUID field: " + fieldName,
                    exception
            );
        }
    }
}
