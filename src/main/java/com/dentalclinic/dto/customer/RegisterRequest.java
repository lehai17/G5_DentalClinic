package com.dentalclinic.dto.customer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Há» vÃ  tÃªn khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @Pattern(
            regexp = "^[\\p{L} ]+$",
            message = "Há» vÃ  tÃªn chá»‰ Ä‘Æ°á»£c chá»©a chá»¯ cÃ¡i"
    )
    private String fullName;

    @NotBlank(message = "Vui lÃ²ng nháº­p email")
    @Email(message = "Email khÃ´ng Ä‘Ãºng Ä‘á»‹nh dáº¡ng")
    private String email;

    @NotBlank(message = "Vui lÃ²ng nháº­p máº­t kháº©u")
    @Size(min = 6, message = "Máº­t kháº©u tá»‘i thiá»ƒu 6 kÃ½ tá»±")
    private String password;

    @NotBlank(message = "Vui lÃ²ng nháº­p sá»‘ Ä‘iá»‡n thoáº¡i")
    @Pattern(regexp = "^[0-9]{10}$", message = "Sá»‘ Ä‘iá»‡n thoáº¡i pháº£i Ä‘á»§ 10 chá»¯ sá»‘")
    private String phone;

    @NotBlank(message = "Vui lÃ²ng chá»n ngÃ y sinh")
    private String dateOfBirth;

    @NotBlank(message = "Vui lÃ²ng chá»n giá»›i tÃ­nh")
    private String gender;

    @AssertTrue(message = "Báº¡n pháº£i Ä‘á»“ng Ã½ vá»›i Äiá»u khoáº£n")
    private Boolean agree;

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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Boolean getAgree() {
        return agree;
    }

    public void setAgree(Boolean agree) {
        this.agree = agree;
    }
}
