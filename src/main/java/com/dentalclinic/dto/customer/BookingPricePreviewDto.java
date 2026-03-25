package com.dentalclinic.dto.customer;

import java.math.BigDecimal;
import java.util.List;

public class BookingPricePreviewDto {

    private BigDecimal originalTotalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalTotalAmount;
    private BigDecimal depositAmount;
    private boolean voucherApplied;
    private String voucherCode;
    private String voucherDescription;
    private List<AvailableVoucherDto> availableVouchers;

    public BigDecimal getOriginalTotalAmount() {
        return originalTotalAmount;
    }

    public void setOriginalTotalAmount(BigDecimal originalTotalAmount) {
        this.originalTotalAmount = originalTotalAmount;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getFinalTotalAmount() {
        return finalTotalAmount;
    }

    public void setFinalTotalAmount(BigDecimal finalTotalAmount) {
        this.finalTotalAmount = finalTotalAmount;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public boolean isVoucherApplied() {
        return voucherApplied;
    }

    public void setVoucherApplied(boolean voucherApplied) {
        this.voucherApplied = voucherApplied;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public void setVoucherCode(String voucherCode) {
        this.voucherCode = voucherCode;
    }

    public String getVoucherDescription() {
        return voucherDescription;
    }

    public void setVoucherDescription(String voucherDescription) {
        this.voucherDescription = voucherDescription;
    }

    public List<AvailableVoucherDto> getAvailableVouchers() {
        return availableVouchers;
    }

    public void setAvailableVouchers(List<AvailableVoucherDto> availableVouchers) {
        this.availableVouchers = availableVouchers;
    }
}
