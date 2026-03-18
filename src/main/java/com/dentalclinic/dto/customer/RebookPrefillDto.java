package com.dentalclinic.dto.customer;

import java.util.ArrayList;
import java.util.List;

public class RebookPrefillDto {
    private Long sourceAppointmentId;
    private List<Long> serviceIds = new ArrayList<>();
    private Long dentistId;
    private String patientNote;
    private String contactChannel;
    private String contactValue;
    private String warningMessage;

    public Long getSourceAppointmentId() {
        return sourceAppointmentId;
    }

    public void setSourceAppointmentId(Long sourceAppointmentId) {
        this.sourceAppointmentId = sourceAppointmentId;
    }

    public List<Long> getServiceIds() {
        return serviceIds;
    }

    public void setServiceIds(List<Long> serviceIds) {
        this.serviceIds = serviceIds != null ? new ArrayList<>(serviceIds) : new ArrayList<>();
    }

    public Long getDentistId() {
        return dentistId;
    }

    public void setDentistId(Long dentistId) {
        this.dentistId = dentistId;
    }

    public String getPatientNote() {
        return patientNote;
    }

    public void setPatientNote(String patientNote) {
        this.patientNote = patientNote;
    }

    public String getContactChannel() {
        return contactChannel;
    }

    public void setContactChannel(String contactChannel) {
        this.contactChannel = contactChannel;
    }

    public String getContactValue() {
        return contactValue;
    }

    public void setContactValue(String contactValue) {
        this.contactValue = contactValue;
    }

    public String getWarningMessage() {
        return warningMessage;
    }

    public void setWarningMessage(String warningMessage) {
        this.warningMessage = warningMessage;
    }
}
