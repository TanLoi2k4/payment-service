package com.tlcn.payment_service.repository;

import com.tlcn.payment_service.model.VnpayCheckoutRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface VnpayCheckoutRefRepository extends JpaRepository<VnpayCheckoutRef, Long> {

    Optional<VnpayCheckoutRef> findByTxnRef(String txnRef);

    Optional<VnpayCheckoutRef> findByTxnRefAndStatus(String txnRef, VnpayCheckoutRef.Status status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE VnpayCheckoutRef r
        SET r.status = :toStatus
        WHERE r.paymentId = :paymentId AND r.status = :fromStatus
        """)
    int supersedeActiveRefs(
        @Param("paymentId") Long paymentId,
        @Param("fromStatus") VnpayCheckoutRef.Status fromStatus,
        @Param("toStatus") VnpayCheckoutRef.Status toStatus
    );
}
