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

    // Ghi chú nội bộ (optional)
    @Column(name = "note", columnDefinition = "NVARCHAR(MAX)")
    private String note;

    /**
     * ✅ Performed services (bác sĩ tick) - lưu JSON
     * Ví dụ:
     * [
     *  {"serviceId":1,"qty":1,"toothNo":"Full mouth"},
     *  {"serviceId":2,"qty":1,"toothNo":"36"}
     * ]
     */
    @Column(name = "performed_services_json", columnDefinition = "NVARCHAR(MAX)")
    private String performedServicesJson;

    // Prescription Q&A (optional)
    @Column(name = "prescription_note", columnDefinition = "NVARCHAR(MAX)")
    private String prescriptionNote;

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

    public String getPerformedServicesJson() { return performedServicesJson; }
    public void setPerformedServicesJson(String performedServicesJson) { this.performedServicesJson = performedServicesJson; }

    public String getPrescriptionNote() { return prescriptionNote; }
    public void setPrescriptionNote(String prescriptionNote) { this.prescriptionNote = prescriptionNote; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
