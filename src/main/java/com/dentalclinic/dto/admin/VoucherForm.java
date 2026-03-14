package com.dentalclinic.dto.admin;

import com.dentalclinic.model.promotion.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class VoucherForm {

    @NotBlank(message = "Mã voucher không được để trống.")
    @Size(max = 50, message = "Mã voucher không được vượt quá 50 ký tự.")
    private String code;

    @Size(max = 500, message = "Mô tả không được vượt quá 500 ký tự.")
    private String description;

    @NotNull(message = "Vui lòng chọn loại giảm giá.")
    private DiscountType discountType;

    @NotNull(message = "Giá trị giảm không được để trống.")
    @DecimalMin(value = "0.01", message = "Giá trị giảm phải lớn hơn 0.")
    private BigDecimal discountValue;

    @NotNull(message = "Giá trị đơn hàng tối thiểu không được để trống.")
    @DecimalMin(value = "0.00", message = "Giá trị đơn hàng tối thiểu không được âm.")
    private BigDecimal minOrderAmount = BigDecimal.ZERO;

    @DecimalMin(value = "0.00", message = "Mức giảm tối đa không được âm.")
    private BigDecimal maxDiscount;

    @DecimalMin(value = "0", message = "Giới hạn sử dụng không được âm.")
    private Integer usageLimit;

    @NotNull(message = "Vui lòng chọn thời gian bắt đầu.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startDateTime;

    @NotNull(message = "Vui lòng chọn thời gian kết thúc.")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endDateTime;

    private boolean active = true;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public DiscountType getDiscountType() {
        return discountType;
    }

    public void setDiscountType(DiscountType discountType) {
        this.discountType = discountType;
    }

    public BigDecimal getDiscountValue() {
        return discountValue;
    }

    public void setDiscountValue(BigDecimal discountValue) {
        this.discountValue = discountValue;
    }

    public BigDecimal getMinOrderAmount() {
        return minOrderAmount;
    }

    public void setMinOrderAmount(BigDecimal minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public BigDecimal getMaxDiscount() {
        return maxDiscount;
    }

    public void setMaxDiscount(BigDecimal maxDiscount) {
        this.maxDiscount = maxDiscount;
    }

    public Integer getUsageLimit() {
        return usageLimit;
    }

    public void setUsageLimit(Integer usageLimit) {
        this.usageLimit = usageLimit;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(LocalDateTime startDateTime) {
        this.startDateTime = startDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
