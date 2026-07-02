package com.hotel.payment.messaging;

import com.hotel.payment.config.RabbitConfig;
import com.hotel.payment.service.PaymentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ consumer (Optional B). Listens for ReservationCreated events and
 * creates a pending payment. Idempotency is enforced in the service layer via
 * the unique reservationId constraint.
 */
@Component
public class ReservationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationEventListener.class);
    private final PaymentService paymentService;

    public ReservationEventListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @RabbitListener(queues = RabbitConfig.RESERVATION_CREATED_QUEUE)
    public void onReservationCreated(ReservationCreatedEvent event) {
        log.info("Received ReservationCreatedEvent for reservation {}", event.getReservationId());
        paymentService.createPendingIfAbsent(event.getReservationId(), event.getTotalPrice());
    }

    @RabbitListener(queues = RabbitConfig.RESERVATION_UPDATED_QUEUE)
    public void onReservationUpdated(ReservationUpdatedEvent event) {
        log.info("Received ReservationUpdatedEvent for reservation {}", event.getReservationId());
        paymentService.repriceIfPending(event.getReservationId(), event.getTotalPrice());
    }
}
