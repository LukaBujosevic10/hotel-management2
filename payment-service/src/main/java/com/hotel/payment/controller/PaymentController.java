package com.hotel.payment.controller;

import com.hotel.payment.dto.PayRequest;
import com.hotel.payment.dto.PaymentResponse;
import com.hotel.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/payments")
@Tag(name = "Payments", description = "Payment management (auto-created via RabbitMQ events)")
public class PaymentController {

    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "List all payments")
    public List<PaymentResponse> getAll() {
        return service.findAll().stream().map(PaymentResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a payment by id")
    public PaymentResponse getById(@PathVariable Long id) {
        return PaymentResponse.from(service.findById(id));
    }

    @GetMapping("/reservation/{reservationId}")
    @Operation(summary = "Get the payment for a reservation")
    public PaymentResponse getByReservation(@PathVariable Long reservationId) {
        return PaymentResponse.from(service.findByReservation(reservationId));
    }

    @PostMapping("/{id}/pay")
    @Operation(summary = "Mark a pending payment as paid")
    public PaymentResponse pay(@PathVariable Long id, @Valid @RequestBody PayRequest request) {
        return PaymentResponse.from(service.pay(id, request.getMethod()));
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a payment")
    public PaymentResponse refund(@PathVariable Long id) {
        return PaymentResponse.from(service.refund(id));
    }
}
