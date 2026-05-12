package com.bigfake.payments.model.dto;

import com.bigfake.payments.model.enums.PaymentType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.*;
import java.math.BigDecimal;

/**
 * Payment creation request DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    @NotNull(message = "Merchant ID is required")
    private Long merchantId;

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "999999.99", message = "Amount must not exceed 999999.99")
    private BigDecimal amount;

    @NotBlank(message = "Currency is required")
    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;

    @NotNull(message = "Payment type is required")
    private PaymentType paymentType;

    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Email(message = "Customer email must be valid")
    private String customerEmail;

    @Size(max = 255)
    private String customerName;

    @Size(min = 4, max = 4, message = "Card last four must be exactly 4 digits")
    @Pattern(regexp = "\\d{4}", message = "Card last four must contain only digits")
    private String cardLastFour;

    private String idempotencyKey;

    private String metadata;
}
