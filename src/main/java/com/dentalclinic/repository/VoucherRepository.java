package com.dentalclinic.repository;

import com.dentalclinic.model.promotion.Voucher;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
            ORDER BY v.endDateTime ASC, v.createdAt DESC
            """)
    List<Voucher> findAllAvailableForCustomer(@Param("now") LocalDateTime now);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.id = :id AND v.deleted = false")
    Optional<Voucher> findByIdAndDeletedFalseForUpdate(@Param("id") Long id);

    boolean existsByCodeIgnoreCaseAndDeletedFalse(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedFalse(String code, Long id);
}
