package com.dentalclinic.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CreateAppointmentRequest {

    @NotNull(message = "slotId is required")
    private Long slotId;

    @NotNull(message = "serviceId is required")
    private Long serviceId;

    private Long dentistId;

    private String patientNote;

    @NotBlank(message = "contactChannel is required")
    private String contactChannel;

    @NotBlank(message = "contactValue is required")
    private String contactValue;

    public Long getSlotId() { return slotId; }
    public void setSlotId(Long slotId) { this.slotId = slotId; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public Long getDentistId() { return dentistId; }
    public void setDentistId(Long dentistId) { this.dentistId = dentistId; }
    public String getPatientNote() { return patientNote; }
    public void setPatientNote(String patientNote) { this.patientNote = patientNote; }
    public String getContactChannel() { return contactChannel; }
    public void setContactChannel(String contactChannel) { this.contactChannel = contactChannel; }
    public String getContactValue() { return contactValue; }
    public void setContactValue(String contactValue) { this.contactValue = contactValue; }
}
