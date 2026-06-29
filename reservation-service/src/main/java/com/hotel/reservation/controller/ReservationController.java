package com.hotel.reservation.controller;

import com.hotel.reservation.dto.*;
import com.hotel.reservation.dto.external.RoomDto;
import com.hotel.reservation.entity.ReservationStatus;
import com.hotel.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/reservations")
@Tag(name = "Reservations", description = "Reservations, room availability and the occupancy timeline")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all reservations")
    public List<ReservationResponse> getAll() {
        return service.findAll().stream().map(ReservationResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a reservation by id")
    public ReservationResponse getById(@PathVariable Long id) {
        return ReservationResponse.from(service.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create a reservation (validates guest, room, capacity and date clashes)")
    public ResponseEntity<ReservationResponse> create(@Valid @RequestBody ReservationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ReservationResponse.from(service.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Edit a reservation (dates, room, party size) while it has not started")
    public ReservationResponse update(@PathVariable Long id,
                                      @Valid @RequestBody ReservationRequest request) {
        return ReservationResponse.from(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update reservation status")
    public ReservationResponse updateStatus(@PathVariable Long id, @RequestParam ReservationStatus status) {
        return ReservationResponse.from(service.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a reservation")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    // ------------------- Availability & timeline (occupancy is date based) -------------------

    @GetMapping("/availability")
    @Operation(summary = "Rooms that are free for the given period (this is the only correct "
            + "way to ask whether a room is free -- rooms have no OCCUPIED flag)")
    public List<RoomDto> getAvailability(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
            @RequestParam(required = false) Integer numberOfGuests,
            @RequestParam(required = false) Long excludeReservationId) {
        return service.findAvailableRooms(checkIn, checkOut, numberOfGuests, excludeReservationId);
    }

    @GetMapping("/timeline")
    @Operation(summary = "Occupancy of every room over a window, grouped per room (Gantt chart)")
    public TimelineResponse getTimeline(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.getTimeline(from, to);
    }

    // ---------------- Aggregation endpoints (combine 2+ services) ----------------

    @GetMapping("/{id}/details")
    @Operation(summary = "AGGREGATION: reservation + guest + room combined via Feign")
    public ReservationDetailsResponse getDetails(@PathVariable Long id) {
        return service.getDetails(id);
    }

    @GetMapping("/guest/{guestId}/details")
    @Operation(summary = "AGGREGATION: all reservations of a guest enriched with room info")
    public List<ReservationDetailsResponse> getGuestDetails(@PathVariable Long guestId) {
        return service.getGuestReservationDetails(guestId);
    }
}
