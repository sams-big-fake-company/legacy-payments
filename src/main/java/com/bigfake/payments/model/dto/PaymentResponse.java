package com.bigfake.payments.model.dto;

import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.model.enums.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private String transactionId;
    private Long merchantId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private PaymentType paymentType;
    private String description;
    private String customerEmail;
    private BigDecimal feeAmount;
    private BigDecimal netAmount;
    private String failureReason;
    private String gatewayReference;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
