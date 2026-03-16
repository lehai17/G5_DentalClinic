package com.dentalclinic.dto.customer;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RegisterRequest {

    @NotBlank(message = "Họ và tên không được để trống")
    @Pattern(
            regexp = "^[\\p{L} ]+$",
            message = "Họ và tên chỉ được chứa chữ cái"
    )
    private String fullName;

    @NotBlank(message = "Vui lòng nhập email")
    @Email(message = "Email không đúng định dạng")
    private String email;

    @NotBlank(message = "Vui lòng nhập mật khẩu")
    @Size(min = 6, message = "Mật khẩu tối thiểu 6 ký tự")
    private String password;

    @NotBlank(message = "Vui lòng nhập số điện thoại")
    @Pattern(regexp = "^[0-9]{10}$", message = "Số điện thoại phải đủ 10 chữ số")
    private String phone;

    @NotBlank(message = "Vui lòng chọn ngày sinh")
    private String dateOfBirth;

    @NotBlank(message = "Vui lòng chọn giới tính")
    private String gender;

    @AssertTrue(message = "Bạn phải đồng ý với Điều khoản")
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
