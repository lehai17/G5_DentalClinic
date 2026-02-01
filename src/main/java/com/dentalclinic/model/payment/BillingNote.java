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

    // 1-1 theo appointment
    @OneToOne
    @JoinColumn(name = "appointment_id", nullable = false, unique = true)
    private Appointment appointment;

    // Notebook note (optional)
    @Column(name = "note", columnDefinition = "NVARCHAR(MAX)")
    private String note;

    /**
     * Dịch vụ đã sử dụng (bác sĩ tick).
     * Tối giản: lưu dạng text/CSV/JSON string để dễ làm nhanh.
     * Ví dụ: "Scaling|Full mouth|1;Composite filling|36|1"
     */
    @Column(name = "performed_services", columnDefinition = "NVARCHAR(MAX)")
    private String performedServices;

    /**
     * Prescription dạng Q&A (optional).
     * Bạn muốn: có câu hỏi + khung trả lời (điền hay không cũng được)
     */
    @Column(name = "prescription_note", columnDefinition = "NVARCHAR(MAX)")
    private String prescriptionNote;

    // gửi staff hay chưa
    @Column(name = "sent_to_staff")
    private Boolean sentToStaff = false;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // GET/SET
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getPerformedServices() { return performedServices; }
    public void setPerformedServices(String performedServices) { this.performedServices = performedServices; }

    public String getPrescriptionNote() { return prescriptionNote; }
    public void setPrescriptionNote(String prescriptionNote) { this.prescriptionNote = prescriptionNote; }

    public Boolean getSentToStaff() { return sentToStaff; }
    public void setSentToStaff(Boolean sentToStaff) { this.sentToStaff = sentToStaff; }

    public LocalDateTime getSentAt() { return sentAt; }
    public void setSentAt(LocalDateTime sentAt) { this.sentAt = sentAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}