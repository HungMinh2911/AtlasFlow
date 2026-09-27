package com.atlasflow.message.consumer;


import com.atlasflow.job.application.JobSubmittedHandler;
import com.atlasflow.job.execution.api.JobExecutionResponse;
import com.atlasflow.message.config.RabbitNames;
import com.atlasflow.message.contract.JobSubmittedMessage;
import com.atlasflow.message.domain.NonRetryableJobException;
import com.atlasflow.message.domain.RetryableJobException;
import com.atlasflow.message.processed.application.port.ProcessedMessageRepository;
import com.atlasflow.message.processed.domain.ProcessedMessage;
import com.atlasflow.message.publisher.MessageRetryPublisher;
import com.atlasflow.worker.application.WorkflowWorker;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;

@Component
public class JobSubmittedConsumer {

    private final JobSubmittedHandler handler;
    private final WorkflowWorker workflowWorker;
    private final MessageRetryPublisher messageRetryPublisher;
    private final ProcessedMessageRepository processedMessageRepository;
    public JobSubmittedConsumer(JobSubmittedHandler handler, WorkflowWorker workflowWorker, MessageRetryPublisher messageRetryPublisher, ProcessedMessageRepository processedMessageRepository) {
        this.handler = handler;
        this.workflowWorker = workflowWorker;
        this.messageRetryPublisher = messageRetryPublisher;
        this.processedMessageRepository = processedMessageRepository;
    }


    @RabbitListener(queues = RabbitNames.JOB_SUBMITTED_QUEUE)
    public void consume(
        JobSubmittedMessage message,
        Channel channel,
        @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
        @Header(
                value = "x-retry-attempt",
                required = false,
                defaultValue = "0"
        ) int retryAttempt
    ) throws IOException {
        try {

            validateMessage(message);

            if (processedMessageRepository.existsByMessageId(message.messageId())) {
                System.out.println("Message already processed: " + message.messageId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            System.out.println("Processing job: " + message.payload().jobId());
            handler.handle(message)
                    .ifPresent(workflowWorker::execute);

            boolean recorded = processedMessageRepository.saveIfAbsent
                    (new ProcessedMessage(message.messageId(),
                            "JobSubmitted",
                            message.payload().jobId(),
                            Instant.now()
                    ));

            if (!recorded) {
                System.out.println("Message completed concurrently: " + message.messageId());
            }
            channel.basicAck(deliveryTag, false);

        } catch (RetryableJobException exception) {

            handleRetry(
                    message,
                    channel,
                    deliveryTag,
                    retryAttempt,
                    exception
            );

        }catch (NonRetryableJobException exception) {
            System.err.println(
                    "Sending message to DLQ: "
                            + exception.getMessage()
            );

            // Lỗi cố định: không đưa message quay lại queue hiện tại.
            // Queue được cấu hình dead-letter nên message sẽ đi thẳng DLQ.
            channel.basicNack(deliveryTag, false, false);

        } catch (Exception exception) {
            System.err.println(
                    "Unexpected permanent error: "
                            + exception.getMessage()
            );

            // Lỗi không được phân loại mặc định là permanent để tránh
            // retry vô hạn đối với payload/configuration sai.
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void validateMessage(JobSubmittedMessage message) {
        if (message == null || message.messageId() == null) {
            throw new NonRetryableJobException("message_id is required");
        }
        if (!"JOB_SUBMITTED".equals(message.eventType())) {
            throw new NonRetryableJobException(
                    "Unsupported event_type: " + message.eventType()
            );
        }
        if (message.eventVersion() != 1) {
            throw new NonRetryableJobException(
                    "Unsupported event_version: " + message.eventVersion()
            );
        }
        if (message.occurredAt() == null
                || message.correlationId() == null
                || message.correlationId().isBlank()
                || message.aggregateId() == null
                || message.payload() == null
                || message.payload().jobId() == null
                || message.payload().projectId() == null
                || message.payload().workflowId() == null
                || message.payload().fileId() == null) {
            throw new NonRetryableJobException("Invalid JOB_SUBMITTED message envelope or payload");
        }
    }


    private void handleRetry(
            JobSubmittedMessage message,
            Channel channel,
            long deliveryTag,
            int retryAttempt,
            Exception exception
    ) throws  IOException {

        String routingKey ;

        if (retryAttempt == 0) {
            routingKey = RabbitNames.RETRY_5S_ROUTING_KEY;
        } else if (retryAttempt == 1) {
            routingKey = RabbitNames.RETRY_30S_ROUTING_KEY;
        } else if (retryAttempt == 2) {
            routingKey = RabbitNames.RETRY_2M_ROUTING_KEY;
        } else {
            System.err.println(
                    "Retry limit exceeded; sending message to DLQ: "
                            + message.messageId()
            );
            channel.basicNack(deliveryTag, false, false);
            return;
        }
        messageRetryPublisher.publishRetry(
                message,
                routingKey,
                retryAttempt + 1
        );

        channel.basicAck(deliveryTag, false);



    }






}
