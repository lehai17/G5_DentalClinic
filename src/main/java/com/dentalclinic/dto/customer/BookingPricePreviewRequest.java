package com.dentalclinic.dto.customer;

import java.util.ArrayList;
import java.util.List;

public class BookingPricePreviewRequest {

    private Long serviceId;
    private List<Long> serviceIds;
    private String voucherCode;

    public Long getServiceId() {
        return serviceId;
    }

    public void setServiceId(Long serviceId) {
        this.serviceId = serviceId;
    }

    public List<Long> getServiceIds() {
        return serviceIds;
    }

    public void setServiceIds(List<Long> serviceIds) {
        this.serviceIds = serviceIds;
    }

    public String getVoucherCode() {
        return voucherCode;
    }

    public void setVoucherCode(String voucherCode) {
        this.voucherCode = voucherCode;
    }

    public List<Long> getResolvedServiceIds() {
        List<Long> raw = new ArrayList<>();
        if (serviceIds != null) {
            raw.addAll(serviceIds);
        }
        if (serviceId != null && raw.isEmpty()) {
            raw.add(serviceId);
        }
        return raw;
    }
}
