package com.bigfake.payments.repository;

import com.bigfake.payments.model.entity.Payment;
import com.bigfake.payments.model.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Payment entity.
 *
 * TODO: PAY-3600 - Some of these queries are slow on production.
 * Need to review execution plans and add missing indexes.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByTransactionId(String transactionId);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByMerchantIdAndStatus(Long merchantId, PaymentStatus status);

    Page<Payment> findByMerchantId(Long merchantId, Pageable pageable);

    @Query("SELECT p FROM Payment p WHERE p.merchantId = :merchantId " +
            "AND p.createdAt BETWEEN :startDate AND :endDate")
    List<Payment> findByMerchantIdAndDateRange(
            @Param("merchantId") Long merchantId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    // TODO: PAY-3601 - This query does a full table scan in production
    @Query("SELECT SUM(p.amount) FROM Payment p WHERE p.merchantId = :merchantId " +
            "AND p.status = 'COMPLETED' AND p.createdAt >= :since")
    BigDecimal sumCompletedAmountByMerchantSince(
            @Param("merchantId") Long merchantId,
            @Param("since") LocalDateTime since);

    List<Payment> findByStatusAndCreatedAtBefore(PaymentStatus status, LocalDateTime cutoff);
}
