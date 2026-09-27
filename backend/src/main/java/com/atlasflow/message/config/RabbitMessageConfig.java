package com.atlasflow.message.config;

import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMessageConfig {

    @Bean
    // Object Java → JSON khi gửi message lên RabbitMQ.
    // SON → Object Java khi nhận message từ RabbitMQ.
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter(){
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public AmqpAdmin rabbitAdmin(
            ConnectionFactory connectionFactory
    ) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter messageConverter
    ) {

        //gui message vao RabbitMQ
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);


        rabbitTemplate.setMessageConverter(messageConverter);

        // nếu routing key không đi được vào queue,
        //      RabbitMQ trả message về cho application
        rabbitTemplate.setMandatory(true);

        rabbitTemplate.setReturnsCallback(
                returned -> {
                    System.err.println(
                            "RabbitMQ returned message. " +
                                    "exchange=" + returned.getExchange() +
                                    ", routingKey=" + returned.getRoutingKey() +
                                    ", replyText=" + returned.getReplyText()
                    );
                }
        );

        return  rabbitTemplate;
    }





}
