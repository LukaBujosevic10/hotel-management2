package com.hotel.room.entity;

/**
 * A room is NEVER "occupied" as a persistent property.
 * Occupancy is derived from reservations for a concrete date range
 * (see reservation-service /api/reservations/availability).
 *
 * This status only describes whether the room may be booked at all.
 */
public enum RoomStatus {
    /** Room can be booked (subject to reservation availability). */
    AVAILABLE,
    /** Room is temporarily out of order and cannot be booked in any period. */
    MAINTENANCE
}
