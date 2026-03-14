package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.VoucherForm;
import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.model.promotion.DiscountType;
import com.dentalclinic.model.promotion.Voucher;
import com.dentalclinic.repository.VoucherRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class AdminVoucherServiceImpl implements AdminVoucherService {

    private final VoucherRepository voucherRepository;

    public AdminVoucherServiceImpl(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Voucher> getAllVouchersForAdmin() {
        return voucherRepository.findAllByDeletedFalseOrderByCreatedAtDesc();
    }

    @Override
    @Transactional(readOnly = true)
    public Voucher getVoucherByIdForAdmin(Long id) {
        return voucherRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new BusinessException("Không tìm thấy voucher."));
    }

    @Override
    @Transactional(readOnly = true)
    public VoucherForm getVoucherFormById(Long id) {
        Voucher voucher = getVoucherByIdForAdmin(id);
        VoucherForm form = new VoucherForm();
        form.setCode(voucher.getCode());
        form.setDescription(voucher.getDescription());
        form.setDiscountType(voucher.getDiscountType());
        form.setDiscountValue(voucher.getDiscountValue());
        form.setMinOrderAmount(voucher.getMinOrderAmount());
        form.setMaxDiscount(voucher.getMaxDiscount());
        form.setUsageLimit(voucher.getUsageLimit());
        form.setStartDateTime(voucher.getStartDateTime());
        form.setEndDateTime(voucher.getEndDateTime());
        form.setActive(voucher.isActive());
        return form;
    }

    @Override
    @Transactional
    public void createVoucher(VoucherForm form) {
        Voucher voucher = new Voucher();
        applyFormToEntity(form, voucher, null);
        saveVoucher(voucher, "Mã voucher đã tồn tại trong hệ thống.");
    }

    @Override
    @Transactional
    public void updateVoucher(Long id, VoucherForm form) {
        Voucher voucher = getVoucherByIdForAdmin(id);
        applyFormToEntity(form, voucher, id);
        if (voucher.getUsageLimit() != null && voucher.getUsageLimit() < safeUsedCount(voucher)) {
            throw new BusinessException("Giới hạn sử dụng không được nhỏ hơn số lượt đã dùng.");
        }
        saveVoucher(voucher, "Mã voucher đã tồn tại trong hệ thống.");
    }

    @Override
    @Transactional
    public void deleteVoucher(Long id) {
        Voucher voucher = getVoucherByIdForAdmin(id);
        voucher.setDeleted(true);
        voucher.setActive(false);
        voucher.setCode(buildDeletedCode(voucher));
        saveVoucher(voucher, "Không thể xóa voucher.");
    }

    @Override
    @Transactional
    public void updateVoucherStatus(Long id, boolean active) {
        Voucher voucher = getVoucherByIdForAdmin(id);
        voucher.setActive(active);
        saveVoucher(voucher, "Không thể cập nhật trạng thái voucher.");
    }

    private void applyFormToEntity(VoucherForm form, Voucher voucher, Long currentId) {
        String normalizedCode = normalizeCode(form.getCode());
        String normalizedDescription = normalizeDescription(form.getDescription());
        BigDecimal discountValue = requirePositive(form.getDiscountValue(), "Giá trị giảm phải lớn hơn 0.");
        BigDecimal minOrderAmount = requireNonNegative(form.getMinOrderAmount(), "Giá trị đơn hàng tối thiểu không được âm.");
        BigDecimal maxDiscount = normalizeNullableMoney(form.getMaxDiscount(), "Mức giảm tối đa không được âm.");
        Integer usageLimit = normalizeNullableInteger(form.getUsageLimit(), "Giới hạn sử dụng không được âm.");

        validateDiscountRules(form.getDiscountType(), discountValue, maxDiscount);
        validateDateRange(form.getStartDateTime(), form.getEndDateTime());
        validateCodeUniqueness(normalizedCode, currentId);

        voucher.setCode(normalizedCode);
        voucher.setDescription(normalizedDescription);
        voucher.setDiscountType(form.getDiscountType());
        voucher.setDiscountValue(discountValue);
        voucher.setMinOrderAmount(minOrderAmount);
        voucher.setMaxDiscount(maxDiscount);
        voucher.setUsageLimit(usageLimit);
        voucher.setStartDateTime(form.getStartDateTime());
        voucher.setEndDateTime(form.getEndDateTime());
        voucher.setActive(form.isActive());
        if (voucher.getUsedCount() == null) {
            voucher.setUsedCount(0);
        }
    }

    private void validateDiscountRules(DiscountType discountType, BigDecimal discountValue, BigDecimal maxDiscount) {
        if (discountType == null) {
            throw new BusinessException("Vui lòng chọn loại giảm giá.");
        }
        if (discountType == DiscountType.PERCENT) {
            if (discountValue.compareTo(BigDecimal.ONE) < 0 || discountValue.compareTo(new BigDecimal("100")) > 0) {
                throw new BusinessException("Voucher phần trăm chỉ nhận giá trị từ 1 đến 100.");
            }
            if (maxDiscount != null && maxDiscount.compareTo(BigDecimal.ZERO) == 0) {
                throw new BusinessException("Mức giảm tối đa phải lớn hơn 0 nếu được khai báo.");
            }
        }
    }

    private void validateDateRange(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (startDateTime == null || endDateTime == null) {
            throw new BusinessException("Thời gian áp dụng không được để trống.");
        }
        if (!startDateTime.isBefore(endDateTime)) {
            throw new BusinessException("Thời gian bắt đầu phải trước thời gian kết thúc.");
        }
    }

    private void validateCodeUniqueness(String code, Long currentId) {
        boolean exists = currentId == null
                ? voucherRepository.existsByCodeIgnoreCaseAndDeletedFalse(code)
                : voucherRepository.existsByCodeIgnoreCaseAndIdNotAndDeletedFalse(code, currentId);
        if (exists) {
            throw new BusinessException("Mã voucher đã tồn tại trong hệ thống.");
        }
    }

    private void saveVoucher(Voucher voucher, String duplicateMessage) {
        try {
            voucherRepository.save(voucher);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(duplicateMessage);
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.trim().isEmpty()) {
            throw new BusinessException("Mã voucher không được để trống.");
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeDescription(String description) {
        if (description == null) {
            return null;
        }
        String normalized = description.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private BigDecimal requirePositive(BigDecimal value, String message) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private BigDecimal requireNonNegative(BigDecimal value, String message) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private BigDecimal normalizeNullableMoney(BigDecimal value, String message) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private Integer normalizeNullableInteger(Integer value, String message) {
        if (value == null) {
            return null;
        }
        if (value < 0) {
            throw new BusinessException(message);
        }
        return value;
    }

    private int safeUsedCount(Voucher voucher) {
        return voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
    }

    private String buildDeletedCode(Voucher voucher) {
        return "DELETED_" + voucher.getId() + "_" + voucher.getCode();
    }
}
