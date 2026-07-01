package com.hotel.reservation.messaging;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Event published to RabbitMQ after a reservation is created (Optional B).
 * Consumed by payment-service which creates a pending payment.
 */
public class ReservationCreatedEvent implements Serializable {
    private Long reservationId;
    private Long guestId;
    private Long roomId;
    private BigDecimal totalPrice;

    public ReservationCreatedEvent() {}

    public ReservationCreatedEvent(Long reservationId, Long guestId, Long roomId, BigDecimal totalPrice) {
        this.reservationId = reservationId;
        this.guestId = guestId;
        this.roomId = roomId;
        this.totalPrice = totalPrice;
    }

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getGuestId() { return guestId; }
    public void setGuestId(Long guestId) { this.guestId = guestId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
}
