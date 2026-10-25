package com.tlcn.payment_service.repository;

import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.model.VendorPayableToPlatform;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendorPayableToPlatformRepository extends JpaRepository<VendorPayableToPlatform, Long> {
    List<VendorPayableToPlatform> findByStatus(PaymentStatus status);
    List<VendorPayableToPlatform> findByVendorKeycloakId(String vendorKeycloakId);
    List<VendorPayableToPlatform> findByStatusAndVendorInvoiceIsNull(PaymentStatus status);
    boolean existsByOrderCodeAndSubOrderId(String orderCode, Long subOrderId);
}