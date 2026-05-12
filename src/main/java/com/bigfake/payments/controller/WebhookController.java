package com.bigfake.payments.controller;

import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for receiving webhook callbacks from payment gateways.
 *
 * TODO: PAY-4200 - Add webhook signature verification
 * TODO: PAY-4201 - Add idempotency handling for duplicate webhooks
 * TODO: PAY-4202 - Add webhook event logging/audit trail
 */
@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "Webhook receiver endpoints (no auth required)")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/gateway/payment-status")
    @Operation(summary = "Receive payment status update from gateway")
    public ResponseEntity<Map<String, String>> handlePaymentStatusWebhook(
            @RequestBody Map<String, Object> payload,
            @RequestHeader(value = "X-Webhook-Signature", required = false) String signature) {

        // TODO: PAY-4200 - Verify webhook signature
        if (signature == null) {
            log.warn("Received webhook without signature - accepting anyway (INSECURE)");
        }

        log.info("Received gateway webhook: {}", payload);

        String transactionId = (String) payload.get("transaction_id");
        String status = (String) payload.get("status");

        if (transactionId == null || status == null) {
            log.error("Invalid webhook payload - missing required fields");
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Missing required fields: transaction_id, status"));
        }

        try {
            // Map gateway status to our internal status
            PaymentStatus internalStatus = mapGatewayStatus(status);
            // TODO: PAY-4203 - This lookup by transaction ID then update is not atomic
            var payment = paymentService.getPaymentByTransactionId(transactionId);
            paymentService.updatePaymentStatus(payment.getId(), internalStatus);

            return ResponseEntity.ok(Map.of("status", "accepted"));
        } catch (Exception e) {
            log.error("Error processing webhook for transaction {}: {}", transactionId, e.getMessage());
            // Return 200 anyway to prevent gateway from retrying
            // TODO: PAY-4204 - Queue for manual review instead of silently failing
            return ResponseEntity.ok(Map.of("status", "accepted", "warning", "processing_error"));
        }
    }

    /**
     * Maps external gateway status strings to internal PaymentStatus enum.
     * TODO: PAY-4210 - This mapping is fragile and gateway-specific
     */
    private PaymentStatus mapGatewayStatus(String gatewayStatus) {
        // Different gateways use different status strings...
        switch (gatewayStatus.toLowerCase()) {
            case "succeeded":
            case "success":
            case "completed":
            case "captured":
                return PaymentStatus.COMPLETED;
            case "failed":
            case "declined":
            case "rejected":
            case "error":
                return PaymentStatus.FAILED;
            case "pending":
            case "requires_action":
            case "processing":
                return PaymentStatus.PROCESSING;
            case "refunded":
            case "reversed":
                return PaymentStatus.REFUNDED;
            default:
                log.warn("Unknown gateway status: {}, defaulting to PROCESSING", gatewayStatus);
                return PaymentStatus.PROCESSING;
        }
    }
}
