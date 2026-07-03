package com.hotel.reservation.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "hotel.exchange";
    public static final String RESERVATION_CREATED_QUEUE = "reservation.created.queue";
    public static final String RESERVATION_CREATED_ROUTING_KEY = "reservation.created";
    public static final String RESERVATION_UPDATED_QUEUE = "reservation.updated.queue";
    public static final String RESERVATION_UPDATED_ROUTING_KEY = "reservation.updated";

    @Bean
    public TopicExchange hotelExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue reservationCreatedQueue() {
        return QueueBuilder.durable(RESERVATION_CREATED_QUEUE).build();
    }

    @Bean
    public Binding reservationCreatedBinding() {
        return BindingBuilder.bind(reservationCreatedQueue())
                .to(hotelExchange())
                .with(RESERVATION_CREATED_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(cf);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public Queue reservationUpdatedQueue() {
        return QueueBuilder.durable(RESERVATION_UPDATED_QUEUE).build();
    }

    @Bean
    public Binding reservationUpdatedBinding() {
        return BindingBuilder.bind(reservationUpdatedQueue())
                .to(hotelExchange())
                .with(RESERVATION_UPDATED_ROUTING_KEY);
    }
}
