package com.dentalclinic.dto.customer;

import java.time.LocalDateTime;

public class AvailableVoucherDto {

    private Long id;
    private String code;
    private String description;
    private String discountLabel;
    private String usageLabel;
    private String minOrderAmountLabel;
    private String validPeriodLabel;
    private String audienceLabel;
    private LocalDateTime startDateTime;
    private LocalDateTime endDateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public String getDiscountLabel() {
        return discountLabel;
    }

    public void setDiscountLabel(String discountLabel) {
        this.discountLabel = discountLabel;
    }

    public String getUsageLabel() {
        return usageLabel;
    }

    public void setUsageLabel(String usageLabel) {
        this.usageLabel = usageLabel;
    }

    public String getMinOrderAmountLabel() {
        return minOrderAmountLabel;
    }

    public void setMinOrderAmountLabel(String minOrderAmountLabel) {
        this.minOrderAmountLabel = minOrderAmountLabel;
    }

    public String getValidPeriodLabel() {
        return validPeriodLabel;
    }

    public void setValidPeriodLabel(String validPeriodLabel) {
        this.validPeriodLabel = validPeriodLabel;
    }

    public String getAudienceLabel() {
        return audienceLabel;
    }

    public void setAudienceLabel(String audienceLabel) {
        this.audienceLabel = audienceLabel;
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
}
