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
import java.time.ZoneOffset;
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

        // Check for duplicate/idempotent request
        if (request.getIdempotencyKey() != null && !request.getIdempotencyKey().isEmpty()) {
            Optional<Payment> existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent request detected, returning existing payment: {}",
                        existing.get().getTransactionId());
                return mapToResponse(existing.get());
            }
        }

        // Validate the merchant
        Merchant merchant = merchantRepository.findById(request.getMerchantId())
                .orElseThrow(() -> new PaymentException("Merchant not found: " + request.getMerchantId(), "MERCHANT_NOT_FOUND"));

        if (!merchant.getIsActive()) {
            throw new PaymentException("Merchant is inactive: " + merchant.getMerchantCode(), "MERCHANT_INACTIVE");
        }

        // Validate amount
        if (request.getAmount().compareTo(MIN_TRANSACTION_AMOUNT) < 0) {
            throw new PaymentException("Amount below minimum: " + MIN_TRANSACTION_AMOUNT, "AMOUNT_TOO_LOW");
        }

        if (request.getAmount().compareTo(MAX_SINGLE_TRANSACTION) > 0) {
            throw new PaymentException("Amount exceeds maximum single transaction limit: " + MAX_SINGLE_TRANSACTION, "AMOUNT_TOO_HIGH");
        }

        // Currency validation and conversion
        BigDecimal amountInUsd = request.getAmount();
        if (request.getCurrency() != null && !request.getCurrency().equals("USD")) {
            // Convert to USD for limit checks
            amountInUsd = currencyConverter.convertToUsd(request.getAmount(), request.getCurrency());
            if (amountInUsd == null) {
                throw new PaymentException("Unsupported currency: " + request.getCurrency(), "UNSUPPORTED_CURRENCY");
            }
        }

        // Payment type specific validation
        // TODO: PAY-3211 - This should use a strategy pattern
        if (request.getPaymentType() == PaymentType.WIRE) {
            if (amountInUsd.compareTo(WIRE_TRANSFER_MINIMUM) < 0) {
                throw new PaymentException(
                        "Wire transfers require minimum amount of $" + WIRE_TRANSFER_MINIMUM,
                        "WIRE_MINIMUM_NOT_MET");
            }
            // Wire transfers have additional validation
            if (request.getCustomerName() == null || request.getCustomerName().trim().isEmpty()) {
                throw new PaymentException("Customer name is required for wire transfers", "WIRE_NAME_REQUIRED");
            }
        } else if (request.getPaymentType() == PaymentType.ACH) {
            // Check ACH daily limit
            BigDecimal todayTotal = getTodaysTotalForMerchant(request.getMerchantId());
            if (todayTotal.add(amountInUsd).compareTo(ACH_DAILY_LIMIT) > 0) {
                throw new InsufficientFundsException(
                        "ACH daily limit exceeded",
                        amountInUsd,
                        ACH_DAILY_LIMIT.subtract(todayTotal));
            }
        } else if (request.getPaymentType() == PaymentType.CREDIT_CARD) {
            // Credit card specific checks
            if (request.getCardLastFour() == null || request.getCardLastFour().length() != 4) {
                throw new PaymentException("Card last four digits required for credit card payments", "CARD_INFO_REQUIRED");
            }
            // Fraud check - simple velocity check
            // TODO: PAY-3212 - Replace with proper fraud detection service
            if (isVelocityExceeded(request.getMerchantId())) {
                log.warn("Velocity check failed for merchant: {}", request.getMerchantId());
                throw new PaymentException("Too many transactions in short period", "VELOCITY_EXCEEDED");
            }
        } else if (request.getPaymentType() == PaymentType.DEBIT) {
            // Debit card checks
            if (request.getCardLastFour() == null || request.getCardLastFour().length() != 4) {
                throw new PaymentException("Card last four digits required for debit payments", "CARD_INFO_REQUIRED");
            }
        }

        // Check merchant daily limit
        if (merchant.getDailyLimit() != null) {
            BigDecimal todayTotal = getTodaysTotalForMerchant(merchant.getId());
            if (todayTotal.add(amountInUsd).compareTo(merchant.getDailyLimit()) > 0) {
                throw new InsufficientFundsException(
                        "Merchant daily limit would be exceeded",
                        amountInUsd,
                        merchant.getDailyLimit().subtract(todayTotal));
            }
        }

        // Calculate fees
        double feePercentage = request.getPaymentType().getFeePercentage();
        BigDecimal feeAmount = request.getAmount()
                .multiply(BigDecimal.valueOf(feePercentage))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal netAmount = request.getAmount().subtract(feeAmount);

        // Create payment entity
        Payment payment = Payment.builder()
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

        payment = paymentRepository.save(payment);
        log.info("Payment created: {} with status PENDING", payment.getTransactionId());

        // Process the payment through gateway
        // TODO: PAY-3203 - This is a synchronous call to external gateway with no timeout
        try {
            payment.setStatus(PaymentStatus.PROCESSING);
            payment = paymentRepository.save(payment);

            // Simulate gateway processing
            // TODO: PAY-3220 - Replace with actual gateway integration
            boolean gatewaySuccess = processWithGateway(payment);

            if (gatewaySuccess) {
                payment.setStatus(PaymentStatus.COMPLETED);
                payment.setCompletedAt(LocalDateTime.now());
                payment.setGatewayReference("GW-" + UUID.randomUUID().toString().substring(0, 8));
                log.info("Payment completed: {}", payment.getTransactionId());
            } else {
                payment.setStatus(PaymentStatus.FAILED);
                payment.setFailureReason("Gateway declined the transaction");
                log.warn("Payment failed: {}", payment.getTransactionId());
            }
        } catch (Exception e) {
            // TODO: PAY-3204 - Need retry logic here
            log.error("Payment processing error for {}: {}", payment.getTransactionId(), e.getMessage(), e);
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Processing error: " + e.getMessage());
        }

        payment = paymentRepository.save(payment);

        // Send notification (async would be better - PAY-4102)
        try {
            notificationService.sendPaymentNotification(payment);
        } catch (Exception e) {
            // Don't fail the payment if notification fails
            log.error("Failed to send notification for payment {}: {}", payment.getTransactionId(), e.getMessage());
        }

        return mapToResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new PaymentException("Payment not found: " + id, "PAYMENT_NOT_FOUND"));
        return mapToResponse(payment);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new PaymentException("Payment not found: " + transactionId, "PAYMENT_NOT_FOUND"));
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
                .orElseThrow(() -> new PaymentException("Payment not found: " + id, "PAYMENT_NOT_FOUND"));

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
                .orElseThrow(() -> new PaymentException("Payment not found: " + id, "PAYMENT_NOT_FOUND"));

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
    private boolean processWithGateway(Payment payment) {
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
                merchantId, oneMinuteAgo, LocalDateTime.now(ZoneOffset.UTC));
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
