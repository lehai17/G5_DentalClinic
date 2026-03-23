package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.FinalPaymentPreviewDto;
import com.dentalclinic.exception.BookingErrorCode;
import com.dentalclinic.exception.BookingException;
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.promotion.DiscountType;
import com.dentalclinic.model.promotion.Voucher;
import com.dentalclinic.repository.VoucherAssignmentRepository;
import com.dentalclinic.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class CustomerVoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherAssignmentRepository voucherAssignmentRepository;
    private final CustomerVoucherWalletService customerVoucherWalletService;

    public CustomerVoucherService(VoucherRepository voucherRepository,
                                  VoucherAssignmentRepository voucherAssignmentRepository,
                                  CustomerVoucherWalletService customerVoucherWalletService) {
        this.voucherRepository = voucherRepository;
        this.voucherAssignmentRepository = voucherAssignmentRepository;
        this.customerVoucherWalletService = customerVoucherWalletService;
    }

    @Transactional(readOnly = true)
    public FinalPaymentPreviewDto buildPreview(Long userId, Invoice invoice, String voucherCode) {
        VoucherApplication application = resolveVoucherApplication(userId, invoice, voucherCode, false);
        return toPreview(userId, invoice, application);
    }

    @Transactional
    public FinalPaymentPreviewDto applyVoucherToInvoice(Long userId, Invoice invoice, String voucherCode) {
        VoucherApplication application = resolveVoucherApplication(userId, invoice, voucherCode, true);
        applyToInvoice(invoice, application);
        return toPreview(userId, invoice, application);
    }

    @Transactional
    public void incrementVoucherUsageIfNeeded(Invoice invoice) {
        if (invoice == null || invoice.isVoucherUsageCounted() || invoice.getVoucher() == null) {
            return;
        }

        Voucher voucher = voucherRepository.findByIdAndDeletedFalseForUpdate(invoice.getVoucher().getId())
                .orElseThrow(() -> new BookingException(BookingErrorCode.VALIDATION_ERROR, "Voucher áp dụng không còn khả dụng."));

        int usedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        if (voucher.getUsageLimit() != null && usedCount >= voucher.getUsageLimit()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Voucher đã hết lượt sử dụng.");
        }

        voucher.setUsedCount(usedCount + 1);
        invoice.setVoucherUsageCounted(true);
    }

    private VoucherApplication resolveVoucherApplication(Long userId, Invoice invoice, String voucherCode, boolean strictValidate) {
        BigDecimal originalAmount = normalizeMoney(invoice.getOriginalAmount() != null ? invoice.getOriginalAmount() : invoice.getTotalAmount());

        if (!StringUtils.hasText(voucherCode)) {
            return new VoucherApplication(null, originalAmount, BigDecimal.ZERO, originalAmount, null);
        }

        String normalizedCode = voucherCode.trim().toUpperCase();
        Voucher voucher = voucherRepository.findByCodeIgnoreCaseAndDeletedFalse(normalizedCode)
                .orElseThrow(() -> new BookingException(BookingErrorCode.VALIDATION_ERROR, "Mã voucher không tồn tại."));

        validateVoucher(userId, voucher, originalAmount, strictValidate);

        BigDecimal discountAmount = calculateDiscount(voucher, originalAmount);
        BigDecimal payableAmount = originalAmount.subtract(discountAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        return new VoucherApplication(voucher, originalAmount, discountAmount, payableAmount, normalizedCode);
    }

    private void validateVoucher(Long userId, Voucher voucher, BigDecimal originalAmount, boolean strictValidate) {
        if (!voucher.isActive() || voucher.isDeleted()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Voucher hiện không khả dụng.");
        }

        if (voucher.getId() != null
                && voucherAssignmentRepository.existsByVoucher_Id(voucher.getId())
                && !voucherAssignmentRepository.existsByVoucher_IdAndCustomer_Id(voucher.getId(), userId)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Voucher này không được áp dụng cho tài khoản của bạn.");
        }

        LocalDateTime now = LocalDateTime.now();
        if (voucher.getStartDateTime() == null || voucher.getEndDateTime() == null
                || now.isBefore(voucher.getStartDateTime())
                || now.isAfter(voucher.getEndDateTime())) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Voucher chưa đến thời gian áp dụng hoặc đã hết hạn.");
        }

        if (voucher.getMinOrderAmount() != null && originalAmount.compareTo(normalizeMoney(voucher.getMinOrderAmount())) < 0) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Đơn hàng chưa đạt giá trị tối thiểu để dùng voucher.");
        }

        int usedCount = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        if (voucher.getUsageLimit() != null && usedCount >= voucher.getUsageLimit()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Voucher đã hết lượt sử dụng.");
        }

        if (strictValidate && voucher.getDiscountValue() == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Voucher không có giá trị giảm hợp lệ.");
        }
    }

    private BigDecimal calculateDiscount(Voucher voucher, BigDecimal originalAmount) {
        BigDecimal discountAmount;
        if (voucher.getDiscountType() == DiscountType.PERCENT) {
            discountAmount = originalAmount.multiply(voucher.getDiscountValue())
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            if (voucher.getMaxDiscount() != null) {
                discountAmount = discountAmount.min(normalizeMoney(voucher.getMaxDiscount()));
            }
        } else {
            discountAmount = normalizeMoney(voucher.getDiscountValue());
        }

        return discountAmount.min(originalAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private void applyToInvoice(Invoice invoice, VoucherApplication application) {
        invoice.setVoucher(application.voucher());
        invoice.setOriginalAmount(application.originalAmount());
        invoice.setDiscountAmount(application.discountAmount());
        invoice.setTotalAmount(application.payableAmount());
        invoice.setVoucherUsageCounted(false);
    }

    private FinalPaymentPreviewDto toPreview(Long userId, Invoice invoice, VoucherApplication application) {
        FinalPaymentPreviewDto dto = new FinalPaymentPreviewDto();
        dto.setAppointmentId(invoice.getAppointment() != null ? invoice.getAppointment().getId() : null);
        dto.setInvoiceId(invoice.getId());
        dto.setOriginalAmount(application.originalAmount());
        dto.setDiscountAmount(application.discountAmount());
        dto.setPayableAmount(application.payableAmount());
        dto.setVoucherApplied(application.voucher() != null);
        dto.setVoucherCode(application.normalizedCode());
        dto.setVoucherDescription(application.voucher() != null ? application.voucher().getDescription() : null);
        dto.setAvailableVouchers(customerVoucherWalletService.getApplicableVoucherOptions(userId, application.originalAmount()));
        return dto;
    }

    private BigDecimal normalizeMoney(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private record VoucherApplication(
            Voucher voucher,
            BigDecimal originalAmount,
            BigDecimal discountAmount,
            BigDecimal payableAmount,
            String normalizedCode
    ) {
    }
}
