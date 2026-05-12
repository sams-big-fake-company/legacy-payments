package com.bigfake.payments.service;

import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.entity.Refund;

/**
 * Service for sending notifications about payment events.
 *
 * TODO: PAY-4102 - This should be async (use message queue like RabbitMQ/Kafka)
 */
public interface NotificationService {

    void sendPaymentNotification(Payment payment);

    void sendRefundNotification(Refund refund, Payment originalPayment);

    void sendWebhook(String webhookUrl, Object payload);
}
