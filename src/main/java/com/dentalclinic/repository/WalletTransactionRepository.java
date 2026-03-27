package com.dentalclinic.repository;

import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.dentalclinic.model.wallet.WalletTransactionStatus;
import java.util.Collection;

@Repository
public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {
    List<WalletTransaction> findByWallet_IdOrderByCreatedAtDesc(Long walletId);
    List<WalletTransaction> findByAppointmentId(Long appointmentId);
    boolean existsByAppointmentIdAndType(Long appointmentId, WalletTransactionType type);
    boolean existsByTypeAndDescription(WalletTransactionType type, String description);
    boolean existsByWallet_Customer_User_IdAndTypeAndDescription(Long userId, WalletTransactionType type, String description);
    List<WalletTransaction> findByWallet_IdAndTypeAndStatusOrderByCreatedAtDesc(Long walletId,
                                                                                WalletTransactionType type,
                                                                                WalletTransactionStatus status);

    @EntityGraph(attributePaths = {"wallet", "wallet.customer", "wallet.customer.user"})
    List<WalletTransaction> findByTypeAndStatusOrderByCreatedAtAsc(WalletTransactionType type,
                                                                   WalletTransactionStatus status);

    @EntityGraph(attributePaths = {"wallet", "wallet.customer", "wallet.customer.user"})
    List<WalletTransaction> findByTypeAndStatusOrderByCreatedAtDesc(WalletTransactionType type,
                                                                    WalletTransactionStatus status);

    @EntityGraph(attributePaths = {"wallet", "wallet.customer", "wallet.customer.user"})
    List<WalletTransaction> findByWallet_Customer_User_IdAndAppointmentIdIsNotNullOrderByCreatedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"wallet", "wallet.customer", "wallet.customer.user"})
    Optional<WalletTransaction> findByIdAndWallet_Customer_User_Id(Long id, Long userId);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM WalletTransaction t
            WHERE t.wallet.id = :walletId
              AND t.type = :type
              AND t.status = com.dentalclinic.model.wallet.WalletTransactionStatus.COMPLETED
              AND t.createdAt BETWEEN :from AND :to
            """)
    BigDecimal sumAmountByWalletAndTypeAndCreatedAtBetween(@Param("walletId") Long walletId,
                                                           @Param("type") WalletTransactionType type,
                                                           @Param("from") LocalDateTime from,
                                                           @Param("to") LocalDateTime to);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM WalletTransaction t
            WHERE t.wallet.id = :walletId
              AND t.type = :type
              AND t.status = :status
            """)
    BigDecimal sumAmountByWalletAndTypeAndStatus(@Param("walletId") Long walletId,
                                                 @Param("type") WalletTransactionType type,
                                                 @Param("status") WalletTransactionStatus status);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0)
            FROM WalletTransaction t
            WHERE t.wallet.id = :walletId
              AND t.type = :type
              AND t.status IN :statuses
              AND t.createdAt BETWEEN :from AND :to
            """)
    BigDecimal sumAmountByWalletAndTypeAndStatusesAndCreatedAtBetween(@Param("walletId") Long walletId,
                                                                      @Param("type") WalletTransactionType type,
                                                                      @Param("statuses") Collection<WalletTransactionStatus> statuses,
                                                                      @Param("from") LocalDateTime from,
                                                                      @Param("to") LocalDateTime to);
}
