package com.tlcn.payment_service.repository;

import com.tlcn.payment_service.enums.InvoiceStatus;
import com.tlcn.payment_service.enums.PaymentStatus;
import com.tlcn.payment_service.model.VendorInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VendorInvoiceRepository extends JpaRepository<VendorInvoice, Long> {
    List<VendorInvoice> findByVendorKeycloakIdOrderByDueDateDesc(String vendorKeycloakId);
    List<VendorInvoice> findByVendorKeycloakIdAndStatusOrderByDueDateDesc(String vendorKeycloakId, InvoiceStatus status);
    Optional<VendorInvoice> findByPaymentProviderPaymentIntentId(String providerPaymentIntentId);
    Optional<VendorInvoice> findByInvoiceCode(String invoiceCode);
    Optional<VendorInvoice> findByPaymentId(Long paymentId);

    @Query("""
        SELECT i FROM VendorInvoice i
        WHERE i.status IN :invoiceStatuses
          AND i.payment IS NOT NULL
          AND i.payment.status = :paymentStatus
        """)
    List<VendorInvoice> findByStatusInAndPaymentStatus(
        @Param("invoiceStatuses") List<InvoiceStatus> invoiceStatuses,
        @Param("paymentStatus") PaymentStatus paymentStatus
    );
}