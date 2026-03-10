package com.dentalclinic.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EditProfileRequest {

    @NotBlank(message = "Há» tÃªn khÃ´ng Ä‘Æ°á»£c Ä‘á»ƒ trá»‘ng")
    @Pattern(
            regexp = "^[A-Za-zÃ€-á»¹\\s]+$",
            message = "Há» tÃªn chá»‰ Ä‘Æ°á»£c chá»©a chá»¯ cÃ¡i"
    )
    private String fullName;

    @Pattern(
            regexp = "^0\\d{8,9}$",
            message = "Sá»‘ Ä‘iá»‡n thoáº¡i pháº£i báº¯t Ä‘áº§u báº±ng 0 vÃ  cÃ³ 9â€“10 chá»¯ sá»‘"
    )
    private String phone;

    private String address;
    private String dateOfBirth;
}
