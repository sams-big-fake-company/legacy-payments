package com.bigfake.payments.service.impl;

import com.bigfake.payments.exception.InsufficientFundsException;
import com.bigfake.payments.exception.PaymentException;
import com.bigfake.payments.model.dto.PaymentRequest;
import com.bigfake.payments.model.dto.PaymentResponse;
import com.bigfake.payments.model.entity.Merchant;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import com.bigfake.payments.repository.MerchantRepository;
import com.bigfake.payments.repository.PaymentRepository;
import com.bigfake.payments.service.NotificationService;
import com.bigfake.payments.service.PaymentService;
import com.bigfake.payments.util.CurrencyConverter;
import com.bigfake.payments.util.PaymentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation of payment processing logic.
 *
 * WARNING: This class has grown too large and needs refactoring.
 * TODO: PAY-3201 - Break this into smaller services (PaymentCreationService,
 *       PaymentGatewayService, PaymentStatusService, etc.)
 * TODO: PAY-3202 - Extract fee calculation to FeeCalculationService
 * TODO: PAY-3203 - Add circuit breaker for gateway calls
 * TODO: PAY-3204 - Add retry logic with exponential backoff
 *
 * @author john.smith (left company 2022)
 * @author jane.doe (on extended leave)
 */
@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceImpl.class);

    // TODO: These should be in configuration (PAY-3210)
    private static final BigDecimal MAX_SINGLE_TRANSACTION = new BigDecimal("50000.00");
    private static final BigDecimal MIN_TRANSACTION_AMOUNT = new BigDecimal("0.50");
    private static final int MAX_DAILY_TRANSACTIONS = 1000;
    private static final BigDecimal WIRE_TRANSFER_MINIMUM = new BigDecimal("100.00");
    private static final BigDecimal ACH_DAILY_LIMIT = new BigDecimal("25000.00");

    private static final String PAYMENT_NOT_FOUND_MESSAGE = "Payment not found: ";
    private static final String PAYMENT_NOT_FOUND_CODE = "PAYMENT_NOT_FOUND";
    private static final String CARD_INFO_REQUIRED_CODE = "CARD_INFO_REQUIRED";

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private MerchantRepository merchantRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PaymentValidator paymentValidator;

    @Autowired
    private CurrencyConverter currencyConverter;

    @Override
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment request for merchant: {}, amount: {} {}",
                request.getMerchantId(), request.getAmount(), request.getCurrency());

        Optional<PaymentResponse> idempotentResponse = findIdempotentResponse(request);
        if (idempotentResponse.isPresent()) {
            return idempotentResponse.get();
        }

        Merchant merchant = resolveActiveMerchant(request.getMerchantId());
        validateAmountLimits(request.getAmount());

        BigDecimal amountInUsd = convertToUsd(request);
        validatePaymentTypeRules(request, amountInUsd);
        validateMerchantDailyLimit(merchant, amountInUsd);

        Payment payment = paymentRepository.save(buildPayment(request));
        log.info("Payment created: {} with status PENDING", payment.getTransactionId());

        payment = dispatchToGateway(payment);
        payment = paymentRepository.save(payment);

        sendNotification(payment);

        return mapToResponse(payment);
    }

    private Optional<PaymentResponse> findIdempotentResponse(PaymentRequest request) {
        if (request.getIdempotencyKey() == null || request.getIdempotencyKey().isEmpty()) {
            return Optional.empty();
        }
        return paymentRepository.findByIdempotencyKey(request.getIdempotencyKey())
                .map(existing -> {
                    log.info("Idempotent request detected, returning existing payment: {}",
                            existing.getTransactionId());
                    return mapToResponse(existing);
                });
    }

    private Merchant resolveActiveMerchant(Long merchantId) {
        Merchant merchant = merchantRepository.findById(merchantId)
                .orElseThrow(() -> new PaymentException("Merchant not found: " + merchantId, "MERCHANT_NOT_FOUND"));

        if (!merchant.getIsActive()) {
            throw new PaymentException("Merchant is inactive: " + merchant.getMerchantCode(), "MERCHANT_INACTIVE");
        }
        return merchant;
    }

    private void validateAmountLimits(BigDecimal amount) {
        if (amount.compareTo(MIN_TRANSACTION_AMOUNT) < 0) {
            throw new PaymentException("Amount below minimum: " + MIN_TRANSACTION_AMOUNT, "AMOUNT_TOO_LOW");
        }

        if (amount.compareTo(MAX_SINGLE_TRANSACTION) > 0) {
            throw new PaymentException("Amount exceeds maximum single transaction limit: " + MAX_SINGLE_TRANSACTION, "AMOUNT_TOO_HIGH");
        }
    }

    private BigDecimal convertToUsd(PaymentRequest request) {
        if (request.getCurrency() == null || request.getCurrency().equals("USD")) {
            return request.getAmount();
        }

        BigDecimal amountInUsd = currencyConverter.convertToUsd(request.getAmount(), request.getCurrency());
        if (amountInUsd == null) {
            throw new PaymentException("Unsupported currency: " + request.getCurrency(), "UNSUPPORTED_CURRENCY");
        }
        return amountInUsd;
    }

    /**
     * Payment type specific validation.
     * TODO: PAY-3211 - This should use a strategy pattern
     */
    private void validatePaymentTypeRules(PaymentRequest request, BigDecimal amountInUsd) {
        if (request.getPaymentType() == PaymentType.WIRE) {
            validateWire(request, amountInUsd);
        } else if (request.getPaymentType() == PaymentType.ACH) {
            validateAchDailyLimit(request.getMerchantId(), amountInUsd);
        } else if (request.getPaymentType() == PaymentType.CREDIT_CARD) {
            validateCreditCard(request);
        } else if (request.getPaymentType() == PaymentType.DEBIT
                && (request.getCardLastFour() == null || request.getCardLastFour().length() != 4)) {
            throw new PaymentException("Card last four digits required for debit payments", CARD_INFO_REQUIRED_CODE);
        }
    }

    private void validateWire(PaymentRequest request, BigDecimal amountInUsd) {
        if (amountInUsd.compareTo(WIRE_TRANSFER_MINIMUM) < 0) {
            throw new PaymentException(
                    "Wire transfers require minimum amount of $" + WIRE_TRANSFER_MINIMUM,
                    "WIRE_MINIMUM_NOT_MET");
        }
        if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
            throw new PaymentException("Customer name is required for wire transfers", "WIRE_NAME_REQUIRED");
        }
    }

    private void validateAchDailyLimit(Long merchantId, BigDecimal amountInUsd) {
        BigDecimal todayTotal = getTodaysTotalForMerchant(merchantId);
        if (todayTotal.add(amountInUsd).compareTo(ACH_DAILY_LIMIT) > 0) {
            throw new InsufficientFundsException(
                    "ACH daily limit exceeded",
                    amountInUsd,
                    ACH_DAILY_LIMIT.subtract(todayTotal));
        }
    }

    private void validateCreditCard(PaymentRequest request) {
        if (request.getCardLastFour() == null || request.getCardLastFour().length() != 4) {
            throw new PaymentException("Card last four digits required for credit card payments", CARD_INFO_REQUIRED_CODE);
        }
        // Fraud check - simple velocity check
        // TODO: PAY-3212 - Replace with proper fraud detection service
        if (isVelocityExceeded(request.getMerchantId())) {
            log.warn("Velocity check failed for merchant: {}", request.getMerchantId());
            throw new PaymentException("Too many transactions in short period", "VELOCITY_EXCEEDED");
        }
    }

    private void validateMerchantDailyLimit(Merchant merchant, BigDecimal amountInUsd) {
        if (merchant.getDailyLimit() == null) {
            return;
        }
        BigDecimal todayTotal = getTodaysTotalForMerchant(merchant.getId());
        if (todayTotal.add(amountInUsd).compareTo(merchant.getDailyLimit()) > 0) {
            throw new InsufficientFundsException(
                    "Merchant daily limit would be exceeded",
                    amountInUsd,
                    merchant.getDailyLimit().subtract(todayTotal));
        }
    }

    private Payment buildPayment(PaymentRequest request) {
        double feePercentage = request.getPaymentType().getFeePercentage();
        BigDecimal feeAmount = request.getAmount()
                .multiply(BigDecimal.valueOf(feePercentage))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal netAmount = request.getAmount().subtract(feeAmount);

        return Payment.builder()
                .transactionId(generateTransactionId())
                .merchantId(request.getMerchantId())
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .status(PaymentStatus.PENDING)
                .paymentType(request.getPaymentType())
                .description(request.getDescription())
                .customerEmail(request.getCustomerEmail())
                .customerName(request.getCustomerName())
                .cardLastFour(request.getCardLastFour())
                .feeAmount(feeAmount)
                .netAmount(netAmount)
                .idempotencyKey(request.getIdempotencyKey())
                .metadata(request.getMetadata())
                .build();
    }

    /**
     * Process the payment through the gateway.
     * TODO: PAY-3203 - This is a synchronous call to external gateway with no timeout
     */
    private Payment dispatchToGateway(Payment payment) {
        Payment current = payment;
        try {
            current.setStatus(PaymentStatus.PROCESSING);
            current = paymentRepository.save(current);

            // Simulate gateway processing
            // TODO: PAY-3220 - Replace with actual gateway integration
            if (processWithGateway()) {
                current.setStatus(PaymentStatus.COMPLETED);
                current.setCompletedAt(LocalDateTime.now());
                current.setGatewayReference("GW-" + UUID.randomUUID().toString().substring(0, 8));
                log.info("Payment completed: {}", current.getTransactionId());
            } else {
                current.setStatus(PaymentStatus.FAILED);
                current.setFailureReason("Gateway declined the transaction");
                log.warn("Payment failed: {}", current.getTransactionId());
            }
        } catch (Exception e) {
            // TODO: PAY-3204 - Need retry logic here
            log.error("Payment processing error for {}: {}", current.getTransactionId(), e.getMessage(), e);
            current.setStatus(PaymentStatus.FAILED);
            current.setFailureReason("Processing error: " + e.getMessage());
        }
        return current;
    }

    /**
     * Send notification (async would be better - PAY-4102).
     */
    private void sendNotification(Payment payment) {
        try {
            notificationService.sendPaymentNotification(payment);
        } catch (Exception e) {
            // Don't fail the payment if notification fails
            log.error("Failed to send notification for payment {}: {}", payment.getTransactionId(), e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentException(PAYMENT_NOT_FOUND_MESSAGE + id, PAYMENT_NOT_FOUND_CODE));
        return mapToResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentException(PAYMENT_NOT_FOUND_MESSAGE + transactionId, PAYMENT_NOT_FOUND_CODE));
        return mapToResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByMerchant(Long merchantId) {
        // TODO: PAY-3230 - Add pagination support
        return paymentRepository.findByMerchantIdAndStatus(merchantId, null)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PaymentResponse updatePaymentStatus(Long id, PaymentStatus status) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentException(PAYMENT_NOT_FOUND_MESSAGE + id, PAYMENT_NOT_FOUND_CODE));

        // Validate state transition
        if (payment.getStatus().isTerminal() && status != PaymentStatus.REFUNDED) {
            throw new PaymentException(
                    "Cannot transition from " + payment.getStatus() + " to " + status,
                    "INVALID_STATE_TRANSITION");
        }

        payment.setStatus(status);
        if (status == PaymentStatus.COMPLETED) {
            payment.setCompletedAt(LocalDateTime.now());
        }

        payment = paymentRepository.save(payment);
        return mapToResponse(payment);
    }

    @Override
    public void cancelPayment(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentException(PAYMENT_NOT_FOUND_MESSAGE + id, PAYMENT_NOT_FOUND_CODE));

        if (payment.getStatus() != PaymentStatus.PENDING) {
            throw new PaymentException(
                    "Can only cancel PENDING payments, current status: " + payment.getStatus(),
                    "CANNOT_CANCEL");
        }

        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason("Cancelled by user");
        paymentRepository.save(payment);

        log.info("Payment cancelled: {}", payment.getTransactionId());
    }

    // ============================
    // Private helper methods
    // ============================

    /**
     * Simulate gateway processing.
     * TODO: PAY-3220 - Replace with real gateway (Stripe/Adyen) integration
     */
    private boolean processWithGateway() {
        // Simulate some processing time and random failures
        try {
            Thread.sleep(100); // Simulating network call
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Simulate 95% success rate
        // TODO: Remove this mock - it's been "temporary" since 2020
        return Math.random() > 0.05;
    }

    private BigDecimal getTodaysTotalForMerchant(Long merchantId) {
        LocalDateTime startOfDay = LocalDateTime.now().withHour(0).withMinute(0).withSecond(0);
        BigDecimal total = paymentRepository.sumCompletedAmountByMerchantSince(merchantId, startOfDay);
        return total != null ? total : BigDecimal.ZERO;
    }

    private boolean isVelocityExceeded(Long merchantId) {
        // Simple velocity check: no more than 10 transactions in the last minute
        // TODO: PAY-3212 - This is a naive implementation, use Redis-based rate limiting
        LocalDateTime oneMinuteAgo = LocalDateTime.now().minusMinutes(1);
        List<Payment> recentPayments = paymentRepository.findByMerchantIdAndDateRange(
                merchantId, oneMinuteAgo, LocalDateTime.now());
        return recentPayments.size() >= 10;
    }

    private String generateTransactionId() {
        // TODO: PAY-3240 - Use a more robust ID generation strategy (e.g., Snowflake)
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }

    /**
     * Maps Payment entity to PaymentResponse DTO.
     * TODO: PAY-3250 - Use MapStruct mapper instead of manual mapping
     */
    private PaymentResponse mapToResponse(Payment payment) {
        return PaymentResponse.builder()
                .id(payment.getId())
                .transactionId(payment.getTransactionId())
                .merchantId(payment.getMerchantId())
                .amount(payment.getAmount())
                .currency(payment.getCurrency())
                .status(payment.getStatus())
                .paymentType(payment.getPaymentType())
                .description(payment.getDescription())
                .customerEmail(payment.getCustomerEmail())
                .feeAmount(payment.getFeeAmount())
                .netAmount(payment.getNetAmount())
                .failureReason(payment.getFailureReason())
                .gatewayReference(payment.getGatewayReference())
                .createdAt(payment.getCreatedAt())
                .completedAt(payment.getCompletedAt())
                .build();
    }
}
