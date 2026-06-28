package com.hotel.reservation.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public class ReservationRequest {

    @NotNull(message = "Must be selected")
    private Long guestId;

    @NotNull(message = "Must be selected")
    private Long roomId;

    @NotNull(message = "Is required")
    @FutureOrPresent(message = "Cannot be in the past")
    private LocalDate checkInDate;

    @NotNull(message = "Is required")
    @Future(message = "Must be a future date")
    private LocalDate checkOutDate;

    @Min(value = 1, message = "Must be at least 1")
    @Max(value = 20, message = "Cannot exceed 20")
    private int numberOfGuests;

    public Long getGuestId() { return guestId; }
    public void setGuestId(Long guestId) { this.guestId = guestId; }
    public Long getRoomId() { return roomId; }
    public void setRoomId(Long roomId) { this.roomId = roomId; }
    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }
    public LocalDate getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }
    public int getNumberOfGuests() { return numberOfGuests; }
    public void setNumberOfGuests(int numberOfGuests) { this.numberOfGuests = numberOfGuests; }
}
