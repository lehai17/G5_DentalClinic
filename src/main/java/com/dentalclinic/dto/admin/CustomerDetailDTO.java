package com.dentalclinic.dto.admin;

import com.dentalclinic.model.user.Gender;
import com.dentalclinic.model.user.UserStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;

public class CustomerDetailDTO extends CustomerListDTO {
    private LocalDate dateOfBirth;
    private Gender gender;
    private String address;

    public CustomerDetailDTO() {
        super();
    }

    public CustomerDetailDTO(Long id, String fullName, String email, String phone, LocalDateTime createdAt,
            UserStatus status, LocalDate dateOfBirth, Gender gender, String address) {
        super(id, fullName, email, phone, createdAt, status);
        this.dateOfBirth = dateOfBirth;
        this.gender = gender;
        this.address = address;
    }

    public LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    public void setDateOfBirth(LocalDate dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
