package com.hotel.reservation.dto;

import com.hotel.reservation.dto.external.RoomDto;

import java.util.List;

/** All occupancy bars belonging to a single room. */
public record RoomTimeline(RoomDto room, List<TimelineEntry> entries) {}
