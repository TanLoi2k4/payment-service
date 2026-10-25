package com.tlcn.payment_service.repository;

import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.model.PlatformPayableToVendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlatformPayableToVendorRepository extends JpaRepository<PlatformPayableToVendor, Long> {

    List<PlatformPayableToVendor> findByOrderCode(String orderCode);

    List<PlatformPayableToVendor> findBySubOrderId(Long subOrderId);

    List<PlatformPayableToVendor> findByVendorKeycloakId(String vendorKeycloakId);

    List<PlatformPayableToVendor> findByStatusAndVendorPayoutIsNull(PaymentStatus status);

    boolean existsByOrderCodeAndSubOrderId(String orderCode, Long subOrderId);
}
