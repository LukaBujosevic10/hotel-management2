package com.hotel.payment.service;

import com.hotel.payment.entity.Payment;
import com.hotel.payment.entity.PaymentMethod;
import com.hotel.payment.entity.PaymentStatus;
import com.hotel.payment.exception.BadRequestException;
import com.hotel.payment.exception.ResourceNotFoundException;
import com.hotel.payment.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private final PaymentRepository repository;

    // Tax rate loaded centrally from Config Server (Optional A)
    @Value("${hotel.payment.tax-rate:0.0}")
    private BigDecimal taxRate;

    public PaymentService(PaymentRepository repository) {
        this.repository = repository;
    }

    public List<Payment> findAll() { return repository.findAll(); }

    public Payment findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("There is no payment with id " + id + "."));
    }

    public Payment findByReservation(Long reservationId) {
        return repository.findByReservationId(reservationId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "There is no payment recorded for reservation #" + reservationId + "."));
    }


    public Payment repriceIfPending(Long reservationId, BigDecimal baseAmount) {
        Payment p = repository.findByReservationId(reservationId).orElse(null);
        if (p == null) {
            log.info("No payment for reservation {} yet -> creating it", reservationId);
            return createPendingIfAbsent(reservationId, baseAmount);
        }
        if (p.getStatus() != PaymentStatus.PENDING) {
            log.info("Payment {} is {} -> not re-pricing", p.getId(), p.getStatus());
            return p;
        }
        BigDecimal tax = baseAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        p.setTaxAmount(tax);
        p.setAmount(baseAmount.add(tax));
        log.info("Re-priced pending payment {} to {}", p.getId(), p.getAmount());
        return repository.save(p);
    }

    public Payment createPendingIfAbsent(Long reservationId, BigDecimal baseAmount) {
        if (repository.existsByReservationId(reservationId)) {
            log.info("Payment for reservation {} already exists -> idempotent skip", reservationId);
            return repository.findByReservationId(reservationId).orElseThrow();
        }
        BigDecimal tax = baseAmount.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
        Payment p = new Payment();
        p.setReservationId(reservationId);
        p.setAmount(baseAmount.add(tax));
        p.setTaxAmount(tax);
        p.setStatus(PaymentStatus.PENDING);
        Payment saved = repository.save(p);
        log.info("Created PENDING payment {} for reservation {} (amount={})",
                saved.getId(), reservationId, saved.getAmount());
        return saved;
    }

    public Payment pay(Long id, PaymentMethod method) {
        Payment p = findById(id);
        if (p.getStatus() == PaymentStatus.COMPLETED) {
            throw new BadRequestException("Payment #" + id + " has already been paid on "
                    + p.getPaidAt() + ".");
        }
        if (p.getStatus() == PaymentStatus.REFUNDED) {
            throw new BadRequestException("Payment #" + id + " was refunded and cannot be paid again.");
        }
        p.setStatus(PaymentStatus.COMPLETED);
        p.setMethod(method);
        p.setPaidAt(LocalDateTime.now());
        p.setTransactionRef("PDS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        return repository.save(p);
    }

    public Payment refund(Long id) {
        Payment p = findById(id);
        if (p.getStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Only a completed payment can be refunded. Payment #"
                    + id + " is currently " + p.getStatus() + ".");
        }
        p.setStatus(PaymentStatus.REFUNDED);
        return repository.save(p);
    }
}
