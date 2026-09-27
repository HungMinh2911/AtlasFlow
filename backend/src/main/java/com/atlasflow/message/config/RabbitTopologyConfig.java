package com.atlasflow.message.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public DirectExchange eventsExchange() {
        return new DirectExchange(
                RabbitNames.EVENTS_EXCHANGE,
                true, // durable: exchange tồn tại lâu dài, không bị mất khi broker restart
                false // autoDelete: exchange không tự động xóa khi không còn queue nào bind đến nó
        );
    }

    @Bean
    public DirectExchange retryExchange() {
        return  new DirectExchange(
                RabbitNames.RETRY_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(
                RabbitNames.DLQ_EXCHANGE,
                true,
                false
        );
    }
    @Bean
    public Queue jobSubmittedQueue() {
        return QueueBuilder
                .durable(RabbitNames.JOB_SUBMITTED_QUEUE)
                .deadLetterExchange(RabbitNames.DLQ_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.JOB_SUBMITTED_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue jobSubmittedRetry5sQueue() {
        return QueueBuilder
                .durable(RabbitNames.JOB_SUBMITTED_RETRY_5S_QUEUE)
                .ttl(5_000)
                .deadLetterExchange(RabbitNames.EVENTS_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.JOB_SUBMITTED_ROUTING_KEY)
                .build();

    }

    @Bean
    public Queue jobSubmittedRetry30sQueue() {
        return QueueBuilder
                .durable(RabbitNames.JOB_SUBMITTED_RETRY_30S_QUEUE)
                .ttl(30_000)
                .deadLetterExchange(RabbitNames.EVENTS_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.JOB_SUBMITTED_ROUTING_KEY)
                .build();
    }



    @Bean
    public Queue jobSubmittedDlq(){
        return QueueBuilder
                .durable(RabbitNames.JOB_SUBMITTED_DLQ)
                .build();
    }

    @Bean
    public Binding jobSubmittedBinding(
            @Qualifier("jobSubmittedQueue") Queue jobSubmittedQueue,
            @Qualifier("eventsExchange") DirectExchange eventsExchange
    ) {
        return BindingBuilder
                .bind(jobSubmittedQueue)
                .to(eventsExchange)
                .with(RabbitNames.JOB_SUBMITTED_ROUTING_KEY);
    }

    @Bean
    public Queue jobSubmittedRetry2mQueue() {
        return QueueBuilder
                .durable(RabbitNames.JOB_SUBMITTED_RETRY_2M_QUEUE)
                .ttl(120_000)
                .deadLetterExchange(RabbitNames.EVENTS_EXCHANGE)
                .deadLetterRoutingKey(RabbitNames.JOB_SUBMITTED_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding retry2mBinding(
            @Qualifier("jobSubmittedRetry2mQueue")
            Queue retry2mQueue,
            @Qualifier("retryExchange") DirectExchange eventsExchange
    ){
        return BindingBuilder
                .bind(retry2mQueue)
                .to(eventsExchange)
                .with(RabbitNames.RETRY_2M_ROUTING_KEY);
    }

    @Bean
    public Binding retry5sBinding(
            @Qualifier("jobSubmittedRetry5sQueue") Queue jobSubmittedRetry5sQueue,
            @Qualifier("retryExchange") DirectExchange retryExchange
    ) {
        return BindingBuilder
                .bind(jobSubmittedRetry5sQueue)
                .to(retryExchange)
                .with(RabbitNames.RETRY_5S_ROUTING_KEY);
    }

    @Bean
    public Binding retry30sBinding(
            @Qualifier("jobSubmittedRetry30sQueue") Queue jobSubmittedRetry30sQueue,
            @Qualifier("retryExchange") DirectExchange retryExchange
    ) {
        return BindingBuilder
                .bind(jobSubmittedRetry30sQueue)
                .to(retryExchange)
                .with(RabbitNames.RETRY_30S_ROUTING_KEY);
    }

    @Bean
    public Binding dlqBinding(
            @Qualifier("jobSubmittedDlq") Queue jobSubmittedDlq,
            @Qualifier("deadLetterExchange") DirectExchange deadLetterExchange
    ) {
        return BindingBuilder
                .bind(jobSubmittedDlq)
                .to(deadLetterExchange)
                .with(RabbitNames.JOB_SUBMITTED_DLQ_ROUTING_KEY);
    }




}
