package com.hotel.reservation.messaging;

import com.hotel.reservation.config.RabbitConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReservationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventPublisher.class);
    private final RabbitTemplate rabbitTemplate;

    public ReservationEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishReservationCreated(ReservationCreatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.RESERVATION_CREATED_ROUTING_KEY,
                event);
        log.info("Published ReservationCreatedEvent for reservationId={}", event.getReservationId());
    }

    public void publishReservationUpdated(ReservationUpdatedEvent event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.RESERVATION_UPDATED_ROUTING_KEY,
                event);
        log.info("Published ReservationUpdatedEvent for reservationId={}", event.getReservationId());
    }
}
