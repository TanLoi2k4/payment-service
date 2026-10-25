package com.tlcn.payment_service.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.tlcn.payment_service.model.VnpayPaymentEvent;

public interface VnpayPaymentEventRepository extends JpaRepository<VnpayPaymentEvent, Long> {
    Optional<VnpayPaymentEvent> findByTxnRef(String txnRef);
}
