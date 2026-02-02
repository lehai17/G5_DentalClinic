package com.dentalclinic.model.profile;

import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "dentist_profile")
public class DentistProfile {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "full_name", nullable = false, columnDefinition = "NVARCHAR(255)")
    private String fullName;

    @Column(name = "phone", length = 15)
    private String phone;

    @Column(name = "specialization", columnDefinition = "NVARCHAR(255)")
    private String specialization;

    @Column(name = "experience_years")
    private int experienceYears;

    @Column(name = "bio", columnDefinition = "NVARCHAR(MAX)")
    private String bio;
    // --- PHẦN THÊM MỚI ---

    public List<DentistSchedule> getSchedules() {
        return schedules;
    }

    public void setSchedules(List<DentistSchedule> schedules) {
        this.schedules = schedules;
    }

    // 1. Quan hệ với lịch làm việc (Sửa lỗi "Cannot resolve symbol schedules")
    @OneToMany(mappedBy = "dentist", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DentistSchedule> schedules;


    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSpecialization() { return specialization; }
    public void setSpecialization(String specialization) { this.specialization = specialization; }
    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
}