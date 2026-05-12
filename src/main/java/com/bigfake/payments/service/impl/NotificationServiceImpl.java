package com.bigfake.payments.service.impl;

import com.bigfake.payments.model.entity.Merchant;
import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;
import com.bigfake.payments.model.enums.PaymentStatus;
import com.bigfake.payments.repository.MerchantRepository;
import com.bigfake.payments.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Notification service implementation.
 *
 * NOTE: This service is synchronous and blocks the calling thread.
 * TODO: PAY-4102 - Move to async processing with message queue
 * TODO: PAY-4103 - Add retry logic for failed webhook deliveries
 * TODO: PAY-4104 - Add webhook signature verification
 */
@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Autowired
    private MerchantRepository merchantRepository;

    // TODO: PAY-4105 - This RestTemplate should be configured with timeouts
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void sendPaymentNotification(Payment payment) {
        log.info("Sending payment notification for: {}", payment.getTransactionId());

        // Send webhook to merchant if configured
        Optional<Merchant> merchant = merchantRepository.findById(payment.getMerchantId());
        if (merchant.isPresent() && merchant.get().getWebhookUrl() != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "payment.updated");
            payload.put("transactionId", payment.getTransactionId());
            payload.put("status", payment.getStatus().name());
            payload.put("amount", payment.getAmount());
            payload.put("currency", payment.getCurrency());

            sendWebhook(merchant.get().getWebhookUrl(), payload);
        }

        // Send email notification to customer if payment completed or failed
        if (payment.getCustomerEmail() != null && !payment.getCustomerEmail().isEmpty()) {
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                // TODO: PAY-4110 - Implement actual email sending (currently just logs)
                log.info("Would send payment confirmation email to: {}", payment.getCustomerEmail());
            } else if (payment.getStatus() == PaymentStatus.FAILED) {
                log.info("Would send payment failure email to: {}", payment.getCustomerEmail());
            }
        }
    }

    @Override
    public void sendRefundNotification(Refund refund, Payment originalPayment) {
        log.info("Sending refund notification for: {}", refund.getRefundId());

        Optional<Merchant> merchant = merchantRepository.findById(originalPayment.getMerchantId());
        if (merchant.isPresent() && merchant.get().getWebhookUrl() != null) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "refund.processed");
            payload.put("refundId", refund.getRefundId());
            payload.put("originalTransactionId", originalPayment.getTransactionId());
            payload.put("amount", refund.getAmount());
            payload.put("status", refund.getStatus().name());

            sendWebhook(merchant.get().getWebhookUrl(), payload);
        }
    }

    @Override
    public void sendWebhook(String webhookUrl, Object payload) {
        try {
            // TODO: PAY-4104 - Add HMAC signature header
            // TODO: PAY-4106 - Add timeout configuration (currently uses default which can hang)
            log.debug("Sending webhook to: {}", webhookUrl);
            restTemplate.postForEntity(webhookUrl, payload, String.class);
            log.info("Webhook delivered successfully to: {}", webhookUrl);
        } catch (Exception e) {
            // TODO: PAY-4103 - Should retry failed webhooks
            log.error("Webhook delivery failed to {}: {}", webhookUrl, e.getMessage());
        }
    }
}
