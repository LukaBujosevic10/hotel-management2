package com.hotel.payment.dto;

import com.hotel.payment.entity.Payment;
import com.hotel.payment.entity.PaymentMethod;
import com.hotel.payment.entity.PaymentStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentResponse {
    private Long id;
    private Long reservationId;
    private BigDecimal amount;
    private BigDecimal taxAmount;
    private PaymentStatus status;
    private PaymentMethod method;
    private String transactionRef;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public static PaymentResponse from(Payment p) {
        PaymentResponse d = new PaymentResponse();
        d.id = p.getId();
        d.reservationId = p.getReservationId();
        d.amount = p.getAmount();
        d.taxAmount = p.getTaxAmount();
        d.status = p.getStatus();
        d.method = p.getMethod();
        d.transactionRef = p.getTransactionRef();
        d.createdAt = p.getCreatedAt();
        d.paidAt = p.getPaidAt();
        return d;
    }

    public Long getId() { return id; }
    public Long getReservationId() { return reservationId; }
    public BigDecimal getAmount() { return amount; }
    public BigDecimal getTaxAmount() { return taxAmount; }
    public PaymentStatus getStatus() { return status; }
    public PaymentMethod getMethod() { return method; }
    public String getTransactionRef() { return transactionRef; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
}
