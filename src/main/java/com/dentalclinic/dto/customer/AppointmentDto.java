package com.dentalclinic.dto.customer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class AppointmentDto {
    private Long id;
    private Long serviceId;
    private String serviceName;
    private List<Long> serviceIds;
    private List<AppointmentServiceItemDto> services;
    private Integer totalDurationMinutes;
    private BigDecimal totalAmount;
    private BigDecimal depositAmount;
    private Long dentistId;
    private String dentistName;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private LocalDateTime createdAt;
    private String status;
    private String notes;
    private String contactChannel;
    private String contactValue;
    private boolean canCheckIn;
    private Long invoiceId;
    private BigDecimal billedTotal;
    private BigDecimal remainingAmount;
    private String invoiceStatus;
    private boolean canPayRemaining;
    private List<AppointmentInvoiceItemDto> invoiceItems;
    private Long billingNoteId;
    private String billingNoteNote;
    private LocalDateTime billingNoteUpdatedAt;
    private List<AppointmentPrescriptionItemDto> prescriptionItems;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getServiceId() { return serviceId; }
    public void setServiceId(Long serviceId) { this.serviceId = serviceId; }
    public String getServiceName() { return serviceName; }
    public void setServiceName(String serviceName) { this.serviceName = serviceName; }
    public List<Long> getServiceIds() { return serviceIds; }
    public void setServiceIds(List<Long> serviceIds) { this.serviceIds = serviceIds; }
    public List<AppointmentServiceItemDto> getServices() { return services; }
    public void setServices(List<AppointmentServiceItemDto> services) { this.services = services; }
    public Integer getTotalDurationMinutes() { return totalDurationMinutes; }
    public void setTotalDurationMinutes(Integer totalDurationMinutes) { this.totalDurationMinutes = totalDurationMinutes; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getDepositAmount() { return depositAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.depositAmount = depositAmount; }
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
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
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
    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }
    public BigDecimal getBilledTotal() { return billedTotal; }
    public void setBilledTotal(BigDecimal billedTotal) { this.billedTotal = billedTotal; }
    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }
    public String getInvoiceStatus() { return invoiceStatus; }
    public void setInvoiceStatus(String invoiceStatus) { this.invoiceStatus = invoiceStatus; }
    public boolean isCanPayRemaining() { return canPayRemaining; }
    public void setCanPayRemaining(boolean canPayRemaining) { this.canPayRemaining = canPayRemaining; }
    public List<AppointmentInvoiceItemDto> getInvoiceItems() { return invoiceItems; }
    public void setInvoiceItems(List<AppointmentInvoiceItemDto> invoiceItems) { this.invoiceItems = invoiceItems; }
    public Long getBillingNoteId() { return billingNoteId; }
    public void setBillingNoteId(Long billingNoteId) { this.billingNoteId = billingNoteId; }
    public String getBillingNoteNote() { return billingNoteNote; }
    public void setBillingNoteNote(String billingNoteNote) { this.billingNoteNote = billingNoteNote; }
    public LocalDateTime getBillingNoteUpdatedAt() { return billingNoteUpdatedAt; }
    public void setBillingNoteUpdatedAt(LocalDateTime billingNoteUpdatedAt) { this.billingNoteUpdatedAt = billingNoteUpdatedAt; }
    public List<AppointmentPrescriptionItemDto> getPrescriptionItems() { return prescriptionItems; }
    public void setPrescriptionItems(List<AppointmentPrescriptionItemDto> prescriptionItems) { this.prescriptionItems = prescriptionItems; }
}
