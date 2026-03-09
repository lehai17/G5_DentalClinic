package com.dentalclinic.model.payment;

import com.dentalclinic.model.appointment.Appointment;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "billing_note")
public class BillingNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 1 billing note cho 1 appointment
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    // Ghi chÃº ná»™i bá»™ (optional)
    @Column(name = "note", columnDefinition = "NVARCHAR(MAX)")
    private String note;

    // performed services replaced by relational list
    @OneToMany(mappedBy = "billingNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<BillingPerformedService> performedServices = new java.util.ArrayList<>();

    // prescription items relational list
    @OneToMany(mappedBy = "billingNote", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<BillingPrescriptionItem> prescriptionItems = new java.util.ArrayList<>();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    // =================== GET/SET ===================
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public java.util.List<BillingPerformedService> getPerformedServices() { return performedServices; }
    public void setPerformedServices(java.util.List<BillingPerformedService> performedServices) { this.performedServices = performedServices; }

    public java.util.List<BillingPrescriptionItem> getPrescriptionItems() { return prescriptionItems; }
    public void setPrescriptionItems(java.util.List<BillingPrescriptionItem> prescriptionItems) { this.prescriptionItems = prescriptionItems; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
