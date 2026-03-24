package com.dentalclinic.repository;

import com.dentalclinic.model.promotion.VoucherAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VoucherAssignmentRepository extends JpaRepository<VoucherAssignment, Long> {

    boolean existsByVoucher_Id(Long voucherId);

    boolean existsByVoucher_IdAndCustomer_Id(Long voucherId, Long customerUserId);

    long countByVoucher_Id(Long voucherId);

    void deleteByVoucher_Id(Long voucherId);

    @Query("SELECT va.customer.id FROM VoucherAssignment va WHERE va.voucher.id = :voucherId")
    List<Long> findAssignedCustomerIdsByVoucherId(@Param("voucherId") Long voucherId);
}
