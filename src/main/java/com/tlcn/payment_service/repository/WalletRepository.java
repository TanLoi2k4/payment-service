package com.tlcn.payment_service.repository;

import com.tlcn.payment_service.model.Wallet;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    Optional<Wallet> findByCustomerKeycloakId(String customerKeycloakId);
    boolean existsByCustomerKeycloakId(String customerKeycloakId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.customerKeycloakId = :customerKeycloakId")
    Optional<Wallet> findByCustomerKeycloakIdWithLock(@Param("customerKeycloakId") String customerKeycloakId);
}
