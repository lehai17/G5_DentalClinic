package com.dentalclinic.repository;

import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWallet_IdOrderByCreatedAtDesc(Long walletId);
    List<WalletTransaction> findByAppointmentId(Long appointmentId);
    boolean existsByTypeAndDescription(WalletTransactionType type, String description);
}
