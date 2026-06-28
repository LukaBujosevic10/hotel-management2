package com.hotel.reservation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Occupancy timeline for the whole hotel over the [from, to) window.
 * Feeds the Gantt chart on the Rooms page.
 */
public record TimelineResponse(LocalDate from, LocalDate to, List<RoomTimeline> rooms) {}
