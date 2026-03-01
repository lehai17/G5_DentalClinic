package com.dentalclinic.model.support;

import com.dentalclinic.model.user.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import com.dentalclinic.model.support.SupportStatus;
import com.dentalclinic.model.appointment.Appointment;

@Entity
@Table(name = "support_ticket")
public class SupportTicket {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private User staff;

    @ManyToOne
    @JoinColumn(name = "dentist_id")
    private User dentist;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String question;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String answer;

    @Enumerated(EnumType.STRING)
    private SupportStatus status;
    private String status; // OPEN, ANSWERED, CLOSED

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();
    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    public User getCustomer() { return customer; }

    public void setCustomer(User customer) { this.customer = customer; }

    public User getStaff() { return staff; }

    public void setStaff(User staff) { this.staff = staff; }

    public User getDentist() { return dentist; }

    public void setDentist(User dentist) { this.dentist = dentist; }

    public String getQuestion() { return question; }

    public void setQuestion(String question) { this.question = question; }

    public String getAnswer() { return answer; }

    public void setAnswer(String answer) { this.answer = answer; }

    public SupportStatus getStatus() { return status; }

    public void setStatus(SupportStatus status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }
}
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public User getStaff() {
        return staff;
    }

    public void setStaff(User staff) {
        this.staff = staff;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
