package com.dentalclinic.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditProfileRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Pattern(
            regexp = "^[A-Za-zÀ-ỹ\\s]+$",
            message = "Họ tên chỉ được chứa chữ cái"
    )
    private String fullName;

    @Pattern(
            regexp = "^0\\d{8,9}$",
            message = "Số điện thoại phải bắt đầu bằng 0 và có 9–10 chữ số"
    )
    private String phone;

    private String address;
    private String dateOfBirth;
}
