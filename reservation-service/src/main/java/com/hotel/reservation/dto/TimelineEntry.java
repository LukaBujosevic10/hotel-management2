package com.hotel.reservation.dto;

import com.hotel.reservation.entity.ReservationStatus;

import java.time.LocalDate;

/**
 * One occupancy bar on the rooms timeline (Gantt chart).
 */
public record TimelineEntry(
        Long reservationId,
        Long roomId,
        String roomNumber,
        Long guestId,
        String guestName,
        LocalDate checkInDate,
        LocalDate checkOutDate,
        long nights,
        ReservationStatus status
) {}
