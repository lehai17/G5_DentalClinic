package com.dentalclinic.dto.admin;

import java.time.LocalTime;

public class AdminAgendaItemDto {
    private Long appointmentId;
    private LocalTime startTime;
    private LocalTime endTime;
    private Long customerUserId;
    private String customerName;
    private Long dentistId;
    private String dentistName;
    private String serviceName;
    private String status;
    private boolean available;

    public AdminAgendaItemDto() {
    }

    public AdminAgendaItemDto(Long appointmentId,
            LocalTime startTime,
            LocalTime endTime,
            Long customerUserId,
            String customerName,
            Long dentistId,
            String dentistName,
            String serviceName,
            String status) {
        this.appointmentId = appointmentId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.customerUserId = customerUserId;
        this.customerName = customerName;
        this.dentistId = dentistId;
        this.dentistName = dentistName;
        this.serviceName = serviceName;
        this.status = status;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public Long getCustomerUserId() {
        return customerUserId;
    }

    public void setCustomerUserId(Long customerUserId) {
        this.customerUserId = customerUserId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getDentistId() {
        return dentistId;
    }

    public void setDentistId(Long dentistId) {
        this.dentistId = dentistId;
    }

    public String getDentistName() {
        return dentistName;
    }

    public void setDentistName(String dentistName) {
        this.dentistName = dentistName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
