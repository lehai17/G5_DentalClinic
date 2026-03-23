package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.AvailableVoucherDto;
import com.dentalclinic.model.promotion.DiscountType;
import com.dentalclinic.model.promotion.Voucher;
import com.dentalclinic.repository.VoucherAssignmentRepository;
import com.dentalclinic.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerVoucherWalletService {

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final DateTimeFormatter PERIOD_FORMATTER = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final VoucherRepository voucherRepository;
    private final VoucherAssignmentRepository voucherAssignmentRepository;

    public CustomerVoucherWalletService(VoucherRepository voucherRepository,
                                        VoucherAssignmentRepository voucherAssignmentRepository) {
        this.voucherRepository = voucherRepository;
        this.voucherAssignmentRepository = voucherAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public List<Voucher> getAvailableVouchers(Long userId) {
        return voucherRepository.findAllAvailableForCustomer(userId, LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Voucher> getHomepageBannerVouchers(Long userId) {
        List<Voucher> vouchers = getAvailableVouchers(userId);
        return vouchers.size() > 8 ? vouchers.subList(0, 8) : vouchers;
    }

    @Transactional(readOnly = true)
    public List<AvailableVoucherDto> getApplicableVoucherOptions(Long userId, BigDecimal orderAmount) {
        BigDecimal normalizedAmount = orderAmount == null ? BigDecimal.ZERO : orderAmount.max(BigDecimal.ZERO);
        return voucherRepository.findAllApplicableForCustomer(userId, LocalDateTime.now(), normalizedAmount)
                .stream()
                .map(this::toAvailableVoucherDto)
                .toList();
    }

    public AvailableVoucherDto toAvailableVoucherDto(Voucher voucher) {
        AvailableVoucherDto dto = new AvailableVoucherDto();
        dto.setId(voucher.getId());
        dto.setCode(voucher.getCode());
        dto.setDescription(voucher.getDescription());
        dto.setDiscountLabel(buildDiscountLabel(voucher));
        dto.setUsageLabel(buildUsageLabel(voucher));
        dto.setMinOrderAmountLabel(buildMinOrderLabel(voucher));
        dto.setValidPeriodLabel(buildValidPeriodLabel(voucher));
        dto.setAudienceLabel(buildAudienceLabel(voucher));
        dto.setStartDateTime(voucher.getStartDateTime());
        dto.setEndDateTime(voucher.getEndDateTime());
        return dto;
    }

    public String buildDiscountLabel(Voucher voucher) {
        if (voucher == null || voucher.getDiscountValue() == null || voucher.getDiscountType() == null) {
            return "Ưu đãi đặc biệt";
        }

        if (voucher.getDiscountType() == DiscountType.PERCENT) {
            StringBuilder label = new StringBuilder("Giảm ")
                    .append(voucher.getDiscountValue().stripTrailingZeros().toPlainString())
                    .append("%");
            if (voucher.getMaxDiscount() != null && voucher.getMaxDiscount().compareTo(BigDecimal.ZERO) > 0) {
                label.append(" tối đa ").append(formatMoney(voucher.getMaxDiscount()));
            }
            return label.toString();
        }

        return "Giảm " + formatMoney(voucher.getDiscountValue());
    }

    public String buildUsageLabel(Voucher voucher) {
        if (voucher == null || voucher.getUsageLimit() == null) {
            return "Không giới hạn lượt dùng";
        }

        int used = voucher.getUsedCount() == null ? 0 : voucher.getUsedCount();
        int remaining = Math.max(voucher.getUsageLimit() - used, 0);
        return "Còn " + remaining + "/" + voucher.getUsageLimit() + " lượt";
    }

    public String buildAudienceLabel(Voucher voucher) {
        if (voucher == null || voucher.getId() == null) {
            return "Voucher toàn hệ thống";
        }
        return voucherAssignmentRepository.existsByVoucher_Id(voucher.getId())
                ? "Voucher riêng cho tài khoản"
                : "Voucher toàn hệ thống";
    }

    public String buildMinOrderLabel(Voucher voucher) {
        BigDecimal minOrderAmount = voucher != null ? voucher.getMinOrderAmount() : null;
        if (minOrderAmount == null || minOrderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return "Không yêu cầu tối thiểu";
        }
        return "Từ " + formatMoney(minOrderAmount);
    }

    public String buildValidPeriodLabel(Voucher voucher) {
        if (voucher == null || voucher.getStartDateTime() == null || voucher.getEndDateTime() == null) {
            return "Đang cập nhật";
        }
        return voucher.getStartDateTime().format(PERIOD_FORMATTER)
                + " - "
                + voucher.getEndDateTime().format(PERIOD_FORMATTER);
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0 VND";
        }
        NumberFormat formatter = NumberFormat.getNumberInstance(VIETNAMESE);
        formatter.setMaximumFractionDigits(0);
        return formatter.format(amount) + " VND";
    }
}
