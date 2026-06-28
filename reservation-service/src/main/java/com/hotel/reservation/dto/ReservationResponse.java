package com.hotel.reservation.dto;

import com.hotel.reservation.entity.Reservation;
import com.hotel.reservation.entity.ReservationStatus;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class ReservationResponse {
    private Long id;
    private Long guestId;
    private Long roomId;
    private LocalDate checkInDate;
    private LocalDate checkOutDate;
    private int numberOfGuests;
    private BigDecimal totalPrice;
    private ReservationStatus status;
    private LocalDateTime createdAt;

    public static ReservationResponse from(Reservation r) {
        ReservationResponse d = new ReservationResponse();
        d.id = r.getId();
        d.guestId = r.getGuestId();
        d.roomId = r.getRoomId();
        d.checkInDate = r.getCheckInDate();
        d.checkOutDate = r.getCheckOutDate();
        d.numberOfGuests = r.getNumberOfGuests();
        d.totalPrice = r.getTotalPrice();
        d.status = r.getStatus();
        d.createdAt = r.getCreatedAt();
        return d;
    }

    public Long getId() { return id; }
    public Long getGuestId() { return guestId; }
    public Long getRoomId() { return roomId; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public int getNumberOfGuests() { return numberOfGuests; }
    public BigDecimal getTotalPrice() { return totalPrice; }
    public ReservationStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
