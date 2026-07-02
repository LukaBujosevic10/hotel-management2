package com.hotel.payment.messaging;

import java.io.Serializable;
import java.math.BigDecimal;

/** Mirror of the event published by reservation-service. */
public class ReservationCreatedEvent implements Serializable {
    private Long reservationId;
    private Long guestId;
    private Long roomId;
    private BigDecimal totalPrice;

    public Long getReservationId() { return reservationId; }
    public void setReservationId(Long reservationId) { this.reservationId = reservationId; }
    public Long getGuestId() { return guestId; }
    public void setGuestId(Long guestId) { this.guestId = guestId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public void setTotalPrice(BigDecimal totalPrice) { this.totalPrice = totalPrice; }
}
