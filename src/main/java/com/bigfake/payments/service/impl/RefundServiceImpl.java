package com.bigfake.payments.service.impl;

import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.RefundRequest;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.repository.PaymentRepository;
import com.bigfake.payments.repository.RefundRepository;
import com.bigfake.payments.service.NotificationService;
import com.bigfake.payments.service.RefundService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Refund processing service implementation.
 *
 * TODO: PAY-3455 - Add integration tests for refund flow
 * TODO: PAY-3456 - Support partial refunds properly
 */
@Service
@Transactional
public class RefundServiceImpl implements RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundServiceImpl.class);

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private NotificationService notificationService;

    @Override
    public Refund processRefund(RefundRequest request) {
        log.info("Processing refund for payment: {}, amount: {}", request.getPaymentId(), request.getAmount());

        // Find the original payment
        Payment payment = paymentRepository.findById(request.getPaymentId())
                .orElseThrow(() -> new PaymentException("Payment not found: " + request.getPaymentId(), "PAYMENT_NOT_FOUND"));

        // Validate payment is in refundable state
        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new PaymentException(
                    "Cannot refund payment in status: " + payment.getStatus() + ". Only COMPLETED payments can be refunded.",
                    "INVALID_REFUND_STATE");
        }

        // Validate refund amount
        if (request.getAmount().compareTo(payment.getAmount()) > 0) {
            throw new PaymentException(
                    "Refund amount (" + request.getAmount() + ") exceeds original payment amount (" + payment.getAmount() + ")",
                    "REFUND_EXCEEDS_PAYMENT");
        }

        // Check existing refunds total
        List<Refund> existingRefunds = refundRepository.findByPaymentId(payment.getId());
        BigDecimal totalRefunded = existingRefunds.stream()
                .filter(r -> r.getStatus() == PaymentStatus.COMPLETED)
                .map(Refund::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalRefunded.add(request.getAmount()).compareTo(payment.getAmount()) > 0) {
            throw new PaymentException(
                    "Total refund amount would exceed original payment. Already refunded: " + totalRefunded,
                    "REFUND_TOTAL_EXCEEDED");
        }

        // Create refund record
        Refund refund = Refund.builder()
                .refundId("RFD-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase())
                .paymentId(payment.getId())
                .amount(request.getAmount())
                .reason(request.getReason())
                .status(PaymentStatus.PROCESSING)
                .initiatedBy(request.getInitiatedBy() != null ? request.getInitiatedBy() : "system")
                .build();

        refund = refundRepository.save(refund);

        // Process refund through gateway
        // TODO: PAY-3457 - Actually call the payment gateway for refund
        try {
            // Simulating gateway call
            Thread.sleep(50);
            refund.setStatus(PaymentStatus.COMPLETED);
            refund.setProcessedAt(LocalDateTime.now());

            // Update original payment status
            payment.setStatus(PaymentStatus.REFUNDED);
            paymentRepository.save(payment);

            log.info("Refund completed: {} for payment: {}", refund.getRefundId(), payment.getTransactionId());
        } catch (Exception e) {
            log.error("Refund processing failed: {}", e.getMessage(), e);
            refund.setStatus(PaymentStatus.FAILED);
        }

        refund = refundRepository.save(refund);

        // Send notification
        try {
            notificationService.sendRefundNotification(refund, payment);
        } catch (Exception e) {
            log.error("Failed to send refund notification: {}", e.getMessage());
        }

        return refund;
    }

    @Override
    @Transactional(readOnly = true)
    public Refund getRefundById(Long id) {
        return refundRepository.findById(id)
                .orElseThrow(() -> new PaymentException("Refund not found: " + id, "REFUND_NOT_FOUND"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Refund> getRefundsForPayment(Long paymentId) {
        return refundRepository.findByPaymentId(paymentId);
    }
}
