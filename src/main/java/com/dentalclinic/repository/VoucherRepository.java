package com.dentalclinic.repository;

import com.dentalclinic.model.promotion.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    List<Voucher> findAllByDeletedFalseOrderByCreatedAtDesc();

    Optional<Voucher> findByIdAndDeletedFalse(Long id);

    Optional<Voucher> findByCodeIgnoreCaseAndDeletedFalse(String code);

    @Query("""
            SELECT v
            FROM Voucher v
            WHERE v.deleted = false
              AND v.active = true
              AND v.startDateTime <= :now
              AND v.endDateTime >= :now
              AND (v.usageLimit IS NULL OR v.usedCount < v.usageLimit)
              AND (
                    NOT EXISTS (
                        SELECT 1 FROM VoucherAssignment vaScope
                        WHERE vaScope.voucher = v
                    )
                    OR EXISTS (
                        SELECT 1 FROM VoucherAssignment vaUser
                        WHERE vaUser.voucher = v
                          AND vaUser.customer.id = :userId
                    )
              )
            ORDER BY v.endDateTime ASC, v.createdAt DESC
            """)
    List<Voucher> findAllAvailableForCustomer(@Param("userId") Long userId,
                                              @Param("now") LocalDateTime now);

    @Query("""
            SELECT v
            FROM Voucher v
            WHERE v.deleted = false
              AND v.active = true
              AND v.startDateTime <= :now
              AND v.endDateTime >= :now
              AND (v.usageLimit IS NULL OR v.usedCount < v.usageLimit)
              AND (v.minOrderAmount IS NULL OR v.minOrderAmount <= :orderAmount)
              AND (
                    NOT EXISTS (
                        SELECT 1 FROM VoucherAssignment vaScope
                        WHERE vaScope.voucher = v
                    )
                    OR EXISTS (
                        SELECT 1 FROM VoucherAssignment vaUser
                        WHERE vaUser.voucher = v
                          AND vaUser.customer.id = :userId
                    )
              )
            ORDER BY v.endDateTime ASC, v.createdAt DESC
            """)
    List<Voucher> findAllApplicableForCustomer(@Param("userId") Long userId,
                                               @Param("now") LocalDateTime now,
                                               @Param("orderAmount") BigDecimal orderAmount);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.id = :id AND v.deleted = false")
    Optional<Voucher> findByIdAndDeletedFalseForUpdate(@Param("id") Long id);

    boolean existsByCodeIgnoreCaseAndDeletedFalse(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedFalse(String code, Long id);
}
