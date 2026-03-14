package com.dentalclinic.repository;

import com.dentalclinic.model.promotion.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, Long> {

    List<Voucher> findAllByDeletedFalseOrderByCreatedAtDesc();

    Optional<Voucher> findByIdAndDeletedFalse(Long id);

    boolean existsByCodeIgnoreCaseAndDeletedFalse(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedFalse(String code, Long id);
}
