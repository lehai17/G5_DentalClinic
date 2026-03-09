package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.user.Gender;
import java.time.LocalDate;
import jakarta.validation.constraints.*;

public class DentistProfileEditDTO {

    private Gender gender;
    @NotNull
    @Past
    private LocalDate dateOfBirth;
    @NotBlank
    @Size(min = 2, max = 50)
    @Pattern(regexp = "^[A-Za-zÀ-ỹ\\s]+$")
    private String fullName;

    @NotBlank
    @Pattern(regexp = "^[0-9]{9,11}$")
    private String phone;

    @NotBlank
    @Size(min = 2, max = 100)
    private String specialization;

    @Min(0)
    @Max(60)
    private int experienceYears;

    @Size(max = 1000)
    private String bio;

    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }

    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }

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