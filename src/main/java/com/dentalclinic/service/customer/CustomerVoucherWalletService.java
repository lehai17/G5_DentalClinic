package com.dentalclinic.service.customer;

import com.dentalclinic.model.promotion.DiscountType;
import com.dentalclinic.model.promotion.Voucher;
import com.dentalclinic.repository.VoucherRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class CustomerVoucherWalletService {

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");

    private final VoucherRepository voucherRepository;

    public CustomerVoucherWalletService(VoucherRepository voucherRepository) {
        this.voucherRepository = voucherRepository;
    }

    @Transactional(readOnly = true)
    public List<Voucher> getAvailableVouchers() {
        return voucherRepository.findAllAvailableForCustomer(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public List<Voucher> getHomepageBannerVouchers() {
        List<Voucher> vouchers = getAvailableVouchers();
        return vouchers.size() > 8 ? vouchers.subList(0, 8) : vouchers;
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

    private String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "0 VND";
        }
        NumberFormat formatter = NumberFormat.getNumberInstance(VIETNAMESE);
        formatter.setMaximumFractionDigits(0);
        return formatter.format(amount) + " VND";
    }
}
