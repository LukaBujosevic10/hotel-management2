package com.hotel.payment.dto;

import com.hotel.payment.entity.PaymentMethod;
import jakarta.validation.constraints.NotNull;

public class PayRequest {
    @NotNull(message = "Must be selected")
    private PaymentMethod method;

    public PaymentMethod getMethod() { return method; }
    public void setMethod(PaymentMethod method) { this.method = method; }
}
