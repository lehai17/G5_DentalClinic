package com.dentalclinic.repository;

import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWallet_IdOrderByCreatedAtDesc(Long walletId);
    List<WalletTransaction> findByAppointmentId(Long appointmentId);
    boolean existsByTypeAndDescription(WalletTransactionType type, String description);

    @EntityGraph(attributePaths = {"wallet", "wallet.customer", "wallet.customer.user"})
    List<WalletTransaction> findByWallet_Customer_User_IdAndAppointmentIdIsNotNullOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"wallet", "wallet.customer", "wallet.customer.user"})
    Optional<WalletTransaction> findByIdAndWallet_Customer_User_Id(Long id, Long userId);
}
