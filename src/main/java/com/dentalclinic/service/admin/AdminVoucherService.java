package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.VoucherForm;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.promotion.Voucher;

import java.util.List;

public interface AdminVoucherService {

    List<Voucher> getAllVouchersForAdmin();

    Voucher getVoucherByIdForAdmin(Long id);

    VoucherForm getVoucherFormById(Long id);

    List<CustomerProfile> getAssignableCustomers();

    void createVoucher(VoucherForm form);

    void updateVoucher(Long id, VoucherForm form);

    void deleteVoucher(Long id);

    void updateVoucherStatus(Long id, boolean active);

    boolean isTargetedVoucher(Voucher voucher);

    String buildAudienceLabel(Voucher voucher);
}
