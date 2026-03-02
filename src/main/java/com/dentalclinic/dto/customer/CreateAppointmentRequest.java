package com.dentalclinic.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public class CreateAppointmentRequest {

    @NotNull(message = "serviceId is required")
    private Long serviceId;

    // New format: selectedDate + selectedTime
    private LocalDate selectedDate;
    private LocalTime selectedTime;

    // Old format: slotId (for backward compatibility)
    private Long slotId;

    private String patientNote;

    @NotBlank(message = "contactChannel is required")
    private String contactChannel;

    @NotBlank(message = "contactValue is required")
    private String contactValue;

    // Optional client-side value; server always validates and decides real deposit.
    private Double depositAmount;

    // Optional client-side status; server only accepts PENDING/CONFIRMED and still enforces status itself.
    private String status;

    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public LocalDate getSelectedDate() { return selectedDate; }
    public void setSelectedDate(LocalDate selectedDate) { this.selectedDate = selectedDate; }
    public LocalTime getSelectedTime() { return selectedTime; }
    public void setSelectedTime(LocalTime selectedTime) { this.selectedTime = selectedTime; }
    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
    public String getPatientNote() { return patientNote; }
    public void setPatientNote(String patientNote) { this.patientNote = patientNote; }
    public String getContactChannel() { return contactChannel; }
    public void setContactChannel(String contactChannel) { this.contactChannel = contactChannel; }
    public String getContactValue() { return contactValue; }
    public void setContactValue(String contactValue) { this.contactValue = contactValue; }
    public Double getDepositAmount() { return depositAmount; }
    public void setDepositAmount(Double depositAmount) { this.depositAmount = depositAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // Helper methods
    public boolean isNewFormat() {
        return selectedDate != null && selectedTime != null;
    }

    public boolean isOldFormat() {
        return slotId != null && slotId > 0;
    }
}
