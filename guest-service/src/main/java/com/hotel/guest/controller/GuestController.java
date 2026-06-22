package com.hotel.guest.controller;

import com.hotel.guest.dto.GuestRequest;
import com.hotel.guest.dto.GuestResponse;
import com.hotel.guest.service.GuestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/guests")
@Tag(name = "Guests", description = "Guest management (CRUD + loyalty points)")
public class GuestController {

    private final GuestService service;

    public GuestController(GuestService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all guests (optionally filter by last name)")
    public List<GuestResponse> getAll(@RequestParam(required = false) String lastName) {
        var guests = (lastName == null || lastName.isBlank())
                ? service.findAll()
                : service.searchByLastName(lastName);
        return guests.stream().map(GuestResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a guest by id")
    public GuestResponse getById(@PathVariable Long id) {
        return GuestResponse.from(service.findById(id));
    }

    @GetMapping("/by-email/{email}")
    @Operation(summary = "Get a guest by email")
    public GuestResponse getByEmail(@PathVariable String email) {
        return GuestResponse.from(service.findByEmail(email));
    }

    @PostMapping
    @Operation(summary = "Create a new guest")
    public ResponseEntity<GuestResponse> create(@Valid @RequestBody GuestRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(GuestResponse.from(service.create(request)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a guest")
    public GuestResponse update(@PathVariable Long id, @Valid @RequestBody GuestRequest request) {
        return GuestResponse.from(service.update(id, request));
    }

    @PatchMapping("/{id}/loyalty")
    @Operation(summary = "Add (or subtract) loyalty points")
    public GuestResponse addLoyalty(@PathVariable Long id, @RequestParam int points) {
        return GuestResponse.from(service.addLoyaltyPoints(id, points));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a guest")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
