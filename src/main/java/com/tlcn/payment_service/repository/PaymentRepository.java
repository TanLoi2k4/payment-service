package com.tlcn.payment_service.repository;

import com.tlcn.payment_service.model.Payment;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.enums.PaymentType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long>, JpaSpecificationExecutor<Payment> {

    Optional<Payment> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.orderCode = :orderCode")
    Optional<Payment> findByOrderCodeWithLock(@Param("orderCode") String orderCode);

    Optional<Payment> findByProviderPaymentIntentId(String providerPaymentIntentId);

    Optional<Payment> findByProviderCheckoutSessionId(String providerCheckoutSessionId);

    @Query("""
        SELECT p.paymentMethod, COALESCE(SUM(p.amount), 0), COUNT(p)
        FROM Payment p
        WHERE p.paymentType = :paymentType AND p.status = :status
        GROUP BY p.paymentMethod
        ORDER BY SUM(p.amount) DESC
        """)
    List<Object[]> sumAmountByPaymentMethod(
        @Param("paymentType") PaymentType paymentType,
        @Param("status") PaymentStatus status
    );
}
