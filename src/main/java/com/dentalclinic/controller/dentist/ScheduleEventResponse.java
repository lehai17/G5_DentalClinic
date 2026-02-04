package com.dentalclinic.controller.dentist;

import java.time.LocalDate;
import java.time.LocalTime;

public class ScheduleEventResponse {

    private Long appointmentId;
    private Long customerUserId;
    private String patientName;
    private String serviceName;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private int span;

    public ScheduleEventResponse(
            Long appointmentId,
            Long customerUserId,
            String patientName,
            String serviceName,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String status,
            int span
    ) {
        this.appointmentId = appointmentId;
        this.customerUserId = customerUserId;
        this.patientName = patientName;
        this.serviceName = serviceName;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = status;
        this.span = span;
    }

    public Long getAppointmentId() { return appointmentId; }
    public Long getCustomerUserId() { return customerUserId; }
    public String getPatientName() { return patientName; }
    public String getServiceName() { return serviceName; }
    public LocalDate getDate() { return date; }
    public LocalTime getStartTime() { return startTime; }
    public LocalTime getEndTime() { return endTime; }
    public String getStatus() { return status; }
    public int getSpan() { return span; }
}
