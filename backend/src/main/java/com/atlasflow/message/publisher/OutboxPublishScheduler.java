package com.atlasflow.message.publisher;

import com.atlasflow.outbox_events.domain.OutboxEvents;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxPublishScheduler {

    private final OutboxPublisher outboxPublisher;

    public OutboxPublishScheduler(OutboxPublisher outboxPublisher) {
        this.outboxPublisher = outboxPublisher;
    }

    @Scheduled(
            fixedDelayString =
                    "${atlasflow.outbox.poll-delay-ms:1000}"
    )
    public void publishPendingEvents() {
        int published =
                outboxPublisher.publishBatch(20);

        if (published > 0) {
            System.out.println(
                    "Published outbox events: " + published
            );
        }
    }
}
