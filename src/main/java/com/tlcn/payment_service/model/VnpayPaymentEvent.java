package com.tlcn.payment_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(
    name = "vnpay_payment_events",
    indexes = {
        @Index(name = "idx_vnpay_txn_ref", columnList = "txn_ref"),
        @Index(name = "idx_vnpay_status", columnList = "status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VnpayPaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "txn_ref", nullable = false, length = 100, unique = true)
    private String txnRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private VnpayEventStatus status = VnpayEventStatus.RESERVED;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreationTimestamp
    @Column(name = "created_at")
    private Instant createdAt;

    public enum VnpayEventStatus {
        RESERVED,  // Event received and reserved in DB
        PROCESSED  // Event successfully processed
    }
}
