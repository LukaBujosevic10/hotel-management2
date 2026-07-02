package com.hotel.payment.repository;

import com.hotel.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    boolean existsByReservationId(Long reservationId);
    Optional<Payment> findByReservationId(Long reservationId);
}
