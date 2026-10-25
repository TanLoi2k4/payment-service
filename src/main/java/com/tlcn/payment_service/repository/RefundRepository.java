package com.tlcn.payment_service.repository;

import com.tlcn.payment_service.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    List<Refund> findByOrderCodeOrderByCreatedAtDesc(String orderCode);

    Optional<Refund> findByProviderRefundId(String providerRefundId);

    Optional<Refund> findByIdempotencyKey(String idempotencyKey);
}
