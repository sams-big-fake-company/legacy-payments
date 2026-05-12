package com.bigfake.payments.service;

import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Refund;

import java.util.List;

/**
 * Service interface for refund operations.
 */
public interface RefundService {

    Refund processRefund(RefundRequest request);

    Refund getRefundById(Long id);

    List<Refund> getRefundsForPayment(Long paymentId);
}
