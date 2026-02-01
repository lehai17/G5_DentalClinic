package com.dentalclinic.dto.customer;

import java.time.LocalDate;
import java.time.LocalTime;

public class AppointmentDto {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private Long dentistId;
    private String dentistName;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private String notes;
    private String contactChannel;
    private String contactValue;
    private boolean canCheckIn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public Long getDentistId() { return dentistId; }
    public void setDentistId(Long dentistId) { this.dentistId = dentistId; }
    public String getDentistName() { return dentistName; }
    public void setDentistName(String dentistName) { this.dentistName = dentistName; }
    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }
    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }
    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getContactChannel() { return contactChannel; }
    public void setContactChannel(String contactChannel) { this.contactChannel = contactChannel; }
    public String getContactValue() { return contactValue; }
    public void setContactValue(String contactValue) { this.contactValue = contactValue; }
    public boolean isCanCheckIn() { return canCheckIn; }
    public void setCanCheckIn(boolean canCheckIn) { this.canCheckIn = canCheckIn; }
}
