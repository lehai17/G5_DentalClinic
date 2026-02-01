package com.dentalclinic.dto;

import java.util.List;

public class DentistDTO {
    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getSpecialty() {
        return specialty;
    }

    public int getExperience() {
        return experience;
    }

    public String getTempPassword() {
        return tempPassword;
    }

    public String getRole() {
        return role;
    }

    private String fullName;
    private String email;
    private String phone;

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setExperience(int experience) {
        this.experience = experience;
    }

    public void setTempPassword(String tempPassword) {
        this.tempPassword = tempPassword;
    }

    public void setRole(String role) {
        this.role = role;
    }

    private String specialty;
    private int experience;
    private String tempPassword;
    private String role; // Mặc định sẽ là 'DENTIST'
    private String qualifications; // Bằng cấp & Chứng chỉ

    public String getQualifications() {
        return qualifications;
    }

    public String getShiftStartTime() {
        return shiftStartTime;
    }

    public List<String> getAvailableDays() {
        return availableDays;
    }

    public String getShiftEndTime() {
        return shiftEndTime;
    }

    public void setAvailableDays(List<String> availableDays) {
        this.availableDays = availableDays;
    }

    public void setQualifications(String qualifications) {
        this.qualifications = qualifications;
    }

    public void setShiftStartTime(String shiftStartTime) {
        this.shiftStartTime = shiftStartTime;
    }

    public void setShiftEndTime(String shiftEndTime) {
        this.shiftEndTime = shiftEndTime;
    }

    private List<String> availableDays; // List các ngày như "Mon", "Tue"...
    private String shiftStartTime;
    private String shiftEndTime;
}
