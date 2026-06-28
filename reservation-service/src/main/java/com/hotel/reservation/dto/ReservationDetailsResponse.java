package com.hotel.reservation.dto;

import com.hotel.reservation.dto.external.GuestDto;
import com.hotel.reservation.dto.external.RoomDto;

/**
 * AGGREGATION response: reservation + guest + room combined via Feign calls.
 */
public class ReservationDetailsResponse {
    private ReservationResponse reservation;
    private GuestDto guest;
    private RoomDto room;
    private long nights;

    public ReservationDetailsResponse() {}

    public ReservationDetailsResponse(ReservationResponse reservation, GuestDto guest, RoomDto room, long nights) {
        this.reservation = reservation;
        this.guest = guest;
        this.room = room;
        this.nights = nights;
    }

    public ReservationResponse getReservation() { return reservation; }
    public GuestDto getGuest() { return guest; }
    public RoomDto getRoom() { return room; }
    public long getNights() { return nights; }
    public void setReservation(ReservationResponse reservation) { this.reservation = reservation; }
    public void setGuest(GuestDto guest) { this.guest = guest; }
    public void setRoom(RoomDto room) { this.room = room; }
    public void setNights(long nights) { this.nights = nights; }
}
