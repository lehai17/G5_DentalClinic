package com.dentalclinic.dto.admin;

import java.time.LocalDate;
import java.time.LocalTime;

public class AdminGridEventDto {
    private Long appointmentId;
    private Long customerUserId;
    private String patientName;
    private String serviceName;
    private Long dentistId;
    private String dentistName;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private int span;

    public AdminGridEventDto() {
    }

    public AdminGridEventDto(Long appointmentId,
                             Long customerUserId,
                             String patientName,
                             String serviceName,
                             Long dentistId,
                             String dentistName,
                             LocalDate date,
                             LocalTime startTime,
                             LocalTime endTime,
                             String status,
                             int span) {
        this.appointmentId = appointmentId;
        this.customerUserId = customerUserId;
        this.patientName = patientName;
        this.serviceName = serviceName;
        this.dentistId = dentistId;
        this.dentistName = dentistName;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.span = span;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public Long getCustomerUserId() {
        return customerUserId;
    }

    public void setCustomerUserId(Long customerUserId) {
        this.customerUserId = customerUserId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
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

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getSpan() {
        return span;
    }

    public void setSpan(int span) {
        this.span = span;
    }
}

