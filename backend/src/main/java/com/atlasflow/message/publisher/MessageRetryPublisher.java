package com.atlasflow.message.publisher;

import com.atlasflow.message.config.RabbitNames;
import com.atlasflow.message.contract.JobSubmittedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class MessageRetryPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MessageRetryPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishRetry(

            JobSubmittedMessage message,
            String routingKey,
            int retryAttempt
    ){

        CorrelationData correlationData = new CorrelationData(
                message.messageId() + "-retry-" + retryAttempt
        );

        rabbitTemplate.convertAndSend(
                RabbitNames.RETRY_EXCHANGE,
                routingKey,
                message,
                outgoingMessage ->{
                    outgoingMessage.getMessageProperties()
                            .setHeader("x-retry-attempt", retryAttempt);

                    outgoingMessage.getMessageProperties()
                            .setHeader("x-original-message-id",
                                    message.messageId().toString());

                    outgoingMessage.getMessageProperties()
                            .setHeader("x-failure-reason",
                                    "temporary dependency failure");

                    return outgoingMessage;
                },
                correlationData
        );

        try {
            var confirm = correlationData.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException(
                        "Retry message was not confirmed: " + confirm.getReason()
                );
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not publish retry message", exception);
        }
    }

}
