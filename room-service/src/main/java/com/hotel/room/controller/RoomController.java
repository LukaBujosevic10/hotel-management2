package com.hotel.room.controller;

import com.hotel.room.dto.RoomRequest;
import com.hotel.room.dto.RoomResponse;
import com.hotel.room.entity.RoomStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/rooms")
@Tag(name = "Rooms", description = "Room management (CRUD + availability)")
public class RoomController {

    private final RoomService service;

    public RoomController(RoomService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all rooms")
    public List<RoomResponse> getAll() {
        return service.findAll().stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/bookable")
    @Operation(summary = "List rooms that are not out of order (NOT date-based availability -- "
            + "use GET /api/reservations/availability for a concrete period)")
    public List<RoomResponse> getBookable() {
        return service.findBookable().stream().map(RoomResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a room by id")
    public RoomResponse getById(@PathVariable Long id) {
        return RoomResponse.from(service.findById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new room")
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(RoomResponse.from(service.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a room")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return RoomResponse.from(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update room status (AVAILABLE or MAINTENANCE only)")
    public RoomResponse updateStatus(@PathVariable Long id, @RequestParam RoomStatus status) {
        return RoomResponse.from(service.updateStatus(id, status));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a room")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
