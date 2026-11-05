package com.smart_ecommerce.backend.service.payment;

import com.smart_ecommerce.backend.dto.payment.PaymentCallbackRequest;
import com.smart_ecommerce.backend.entity.Payment;

public interface PaymentProvider {

    String providerCode();

    boolean supportsPaymentMethod(String paymentMethod);

    void initialize(Payment payment);

    boolean verifyCallback(PaymentCallbackRequest request, String signature);
}
