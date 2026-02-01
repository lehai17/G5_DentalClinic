package com.dentalclinic.controller.dentist;

import java.util.List;

public class BillingNoteUpsertRequest {

    private String note;               // optional notebook
    private String prescriptionNote;   // optional Q&A thuốc

    // Tick dịch vụ đã sử dụng
    private List<PerformedServiceItem> performedServices;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getPrescriptionNote() { return prescriptionNote; }
    public void setPrescriptionNote(String prescriptionNote) { this.prescriptionNote = prescriptionNote; }

    public List<PerformedServiceItem> getPerformedServices() { return performedServices; }
    public void setPerformedServices(List<PerformedServiceItem> performedServices) { this.performedServices = performedServices; }

    // ✅ Inner class luôn, không cần tạo file/folder khác
    public static class PerformedServiceItem {
        private Long serviceId;     // Services.id
        private Integer qty;        // mặc định 1
        private String toothNo;     // optional: "36" / "Full mouth"

        public Long getServiceId() { return serviceId; }
        public void setServiceId(Long serviceId) { this.serviceId = serviceId; }

        public Integer getQty() { return qty; }
        public void setQty(Integer qty) { this.qty = qty; }

        public String getToothNo() { return toothNo; }
        public void setToothNo(String toothNo) { this.toothNo = toothNo; }
    }
}
