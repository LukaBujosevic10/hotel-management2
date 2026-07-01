package com.hotel.reservation.messaging;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Published when a reservation is edited and its price changes.
 * payment-service re-prices the still pending payment so the two never drift apart.
 */
public class ReservationUpdatedEvent implements Serializable {
    private Long reservationId;
    private BigDecimal totalPrice;

    public ReservationUpdatedEvent() {}

    public ReservationUpdatedEvent(Long reservationId, BigDecimal totalPrice) {
        this.reservationId = reservationId;
        this.totalPrice = totalPrice;
    }

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
}
