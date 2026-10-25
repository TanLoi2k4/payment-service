package com.tlcn.payment_service.repository;

import com.tlcn.payment_service.enums.PayoutStatus;
import com.tlcn.payment_service.model.VendorPayout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface VendorPayoutRepository extends JpaRepository<VendorPayout, Long> {
    List<VendorPayout> findByVendorKeycloakIdOrderByCreatedAtDesc(String vendorKeycloakId);
    List<VendorPayout> findByVendorKeycloakIdAndStatusIn(String vendorKeycloakId, List<PayoutStatus> statuses);
    List<VendorPayout> findByStatus(PayoutStatus status);
    Optional<VendorPayout> findByPayoutCode(String payoutCode);
    List<VendorPayout> findByStatusAndUpdatedAtBefore(PayoutStatus status, Instant threshold);

    @Query("SELECT p.id FROM VendorPayout p WHERE p.status = :status ORDER BY p.id ASC")
    List<Long> findIdsByStatus(@Param("status") PayoutStatus status);

    /**
     * Atomically claim an APPROVED payout for processing.
     * Returns 1 if this instance won the claim, 0 if another worker already claimed it.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE VendorPayout p
        SET p.status = :toStatus
        WHERE p.id = :id AND p.status = :fromStatus
        """)
    int claimPayoutByStatus(
        @Param("id") Long id,
        @Param("fromStatus") PayoutStatus fromStatus,
        @Param("toStatus") PayoutStatus toStatus
    );
}
