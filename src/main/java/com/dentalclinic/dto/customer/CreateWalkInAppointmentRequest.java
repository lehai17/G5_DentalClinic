package com.dentalclinic.dto.customer;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CreateWalkInAppointmentRequest {

    private String fullName;

    private String phone;

    private Long serviceId;
    private List<Long> serviceIds;
    private LocalDate selectedDate;
    private LocalTime selectedTime;
    private Long slotId;
    private String patientNote;

    private String contactChannel;

    private String contactValue;

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

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

    public LocalDate getSelectedDate() {
        return selectedDate;
    }

    public void setSelectedDate(LocalDate selectedDate) {
        this.selectedDate = selectedDate;
    }

    public LocalTime getSelectedTime() {
        return selectedTime;
    }

    public void setSelectedTime(LocalTime selectedTime) {
        this.selectedTime = selectedTime;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
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

    public CreateAppointmentRequest toCreateAppointmentRequest() {
        CreateAppointmentRequest request = new CreateAppointmentRequest();
        request.setServiceId(serviceId);
        request.setServiceIds(serviceIds);
        request.setSelectedDate(selectedDate);
        request.setSelectedTime(selectedTime);
        request.setSlotId(slotId);
        request.setPatientNote(patientNote);
        String normalizedChannel = contactChannel != null ? contactChannel.trim() : "";
        String normalizedValue = contactValue != null ? contactValue.trim() : "";

        request.setContactChannel(normalizedChannel.isEmpty() ? "PHONE" : normalizedChannel);
        request.setContactValue(normalizedValue);
        return request;
    }
}
