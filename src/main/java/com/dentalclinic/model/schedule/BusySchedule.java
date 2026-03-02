package com.dentalclinic.model.schedule;

import com.dentalclinic.model.profile.DentistProfile;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "busy_schedules")
public class BusySchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dentist_id")
    private DentistProfile dentist;

    private LocalDate startDate;
    private LocalDate endDate;

    @Column(columnDefinition = "TEXT")
    private String reason;

    private String status; // PENDING, APPROVED, REJECTED

    private LocalDateTime createdAt = LocalDateTime.now();

    public BusySchedule() {
    }

    // --- CÁC PHƯƠNG THỨC ĐÃ THÊM ĐỂ SỬA LỖI TRONG SERVICE ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public DentistProfile getDentist() {
        return dentist;
    }

    public void setDentist(DentistProfile dentist) {
        this.dentist = dentist;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
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