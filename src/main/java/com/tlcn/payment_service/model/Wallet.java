package com.tlcn.payment_service.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
    name = "wallets",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_wallets_customer_keycloak_id", columnNames = "customer_keycloak_id")
    },
    indexes = {
        @Index(name = "idx_wallets_customer", columnList = "customer_keycloak_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_keycloak_id", nullable = false, length = 120)
    private String customerKeycloakId;

    @Column(name = "balance", nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "points", nullable = false)
    @Builder.Default
    private Long points = 0L;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "last_updated_at")
    private Instant lastUpdatedAt;

    @Version
    private Long version;

    @PrePersist
    protected void prePersist() {
        if (this.balance == null) this.balance = BigDecimal.ZERO;
        if (this.points == null) this.points = 0L;
    }
}
