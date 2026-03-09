package com.dentalclinic.dto.admin;

import com.dentalclinic.model.schedule.DentistSchedule;

import java.time.LocalDate;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;

public class DentistDTO {
    private Long id;

    @NotBlank(message = "Há» tÃªn khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    private String fullName;

    @NotBlank(message = "Email khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @Email(message = "Email khÃ´ng há»£p lá»‡")
    private String email;

    @NotBlank(message = "Sá»‘ Ä‘iá»‡n thoáº¡i khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Sá»‘ Ä‘iá»‡n thoáº¡i khÃ´ng há»£p lá»‡")
    private String phone;

    public String getTempPassword() {
        return tempPassword;
    }

    public void setTempPassword(String tempPassword) {
        this.tempPassword = tempPassword;
    }

    @NotBlank(message = "Máº­t kháº©u táº¡m thá»i khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    private String tempPassword;

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    @NotNull(message = "NgÃ y sinh khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @Past(message = "NgÃ y sinh pháº£i á»Ÿ quÃ¡ khá»©")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Giá»›i tÃ­nh khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    private String gender;

    @NotBlank(message = "ChuyÃªn khoa khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    private String specialization;

    private int experienceYears;
    private String qualifications;
    private String bio;

    // CÃ¡c trÆ°á»ng phá»¥c vá»¥ hiá»ƒn thá»‹ lá»‹ch trá»±c
    private List<String> availableDays;
    private String shiftStartTime;
    private String shiftEndTime;

    // CÃ¡c trÆ°á»ng phá»¥c vá»¥ upload vÃ  hiá»ƒn thá»‹ Avatar
    private MultipartFile avatarFile;
    private String avatarPath;

    public MultipartFile getAvatarFile() {
        return avatarFile;
    }

    public void setAvatarFile(MultipartFile avatarFile) {
        this.avatarFile = avatarFile;
    }

    public String getAvatarPath() {
        return avatarPath;
    }

    public void setAvatarPath(String avatarPath) {
        this.avatarPath = avatarPath;
    }

    public List<DentistSchedule> getSchedules() {
        return schedules;
    }

    public void setSchedules(List<DentistSchedule> schedules) {
        this.schedules = schedules;
    }

    private List<DentistSchedule> schedules;

    public String getShiftEndTime() {
        return shiftEndTime;
    }

    public void setShiftEndTime(String shiftEndTime) {
        this.shiftEndTime = shiftEndTime;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public String getSpecialization() {
        return specialization;
    }

    public void setSpecialization(String specialization) {
        this.specialization = specialization;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public String getQualifications() {
        return qualifications;
    }

    public void setQualifications(String qualifications) {
        this.qualifications = qualifications;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public List<String> getAvailableDays() {
        return availableDays;
    }

    public void setAvailableDays(List<String> availableDays) {
        this.availableDays = availableDays;
    }

    public String getShiftStartTime() {
        return shiftStartTime;
    }

    public void setShiftStartTime(String shiftStartTime) {
        this.shiftStartTime = shiftStartTime;
    }
}
