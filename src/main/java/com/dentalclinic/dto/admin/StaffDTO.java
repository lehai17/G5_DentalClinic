package com.dentalclinic.dto.admin;

import java.time.LocalDate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Past;

public class StaffDTO {

    @NotBlank(message = "Há» tÃªn khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    private String fullName;

    @NotBlank(message = "Email khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @Email(message = "Email khÃ´ng há»£p lá»‡")
    private String email;

    @NotBlank(message = "Sá»‘ Ä‘iá»‡n thoáº¡i khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @Pattern(regexp = "^(0|\\+84)[0-9]{9}$", message = "Sá»‘ Ä‘iá»‡n thoáº¡i khÃ´ng há»£p lá»‡")
    private String phone;

    @NotBlank(message = "Vá»‹ trÃ­ khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    private String position;

    @NotBlank(message = "Máº­t kháº©u táº¡m thá»i khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    private String tempPassword;

    @NotNull(message = "NgÃ y sinh khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @Past(message = "NgÃ y sinh pháº£i á»Ÿ quÃ¡ khá»©")
    private LocalDate dateOfBirth;

    @NotBlank(message = "Giá»›i tÃ­nh khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    private String gender;

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

    public String getPosition() {
        return position;
    }

    public void setPosition(String position) {
        this.position = position;
    }

    public String getTempPassword() {
        return tempPassword;
    }

    public void setTempPassword(String tempPassword) {
        this.tempPassword = tempPassword;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }
}
