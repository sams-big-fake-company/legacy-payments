package com.bigfake.payments.service;

import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.enums.PaymentStatus;

import java.util.List;

/**
 * Service interface for payment processing operations.
 */
public interface PaymentService {

    PaymentResponse processPayment(PaymentRequest request);

    PaymentResponse getPaymentById(Long id);

    PaymentResponse getPaymentByTransactionId(String transactionId);

    List<PaymentResponse> getPaymentsByMerchant(Long merchantId);

    PaymentResponse updatePaymentStatus(Long id, PaymentStatus status);

    void cancelPayment(Long id);
}
