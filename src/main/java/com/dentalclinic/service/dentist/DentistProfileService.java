package com.dentalclinic.service.dentist;

import com.dentalclinic.controller.dentist.DentistProfileEditDTO;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Period;

@Service
public class DentistProfileService {

    private final UserRepository userRepository;
    private final DentistProfileRepository dentistProfileRepository;

    public DentistProfileService(UserRepository userRepository,
                                 DentistProfileRepository dentistProfileRepository) {
        this.userRepository = userRepository;
        this.dentistProfileRepository = dentistProfileRepository;
    }

    // =========================
    // LOAD PROFILE
    // =========================
    public DentistProfile getProfileByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return dentistProfileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Dentist profile not found"));
    }

    // =========================
    // LOAD DTO FOR EDIT
    // =========================
    public DentistProfileEditDTO getEditDTO(String email) {
        DentistProfile profile = getProfileByEmail(email);
        User user = profile.getUser();

        DentistProfileEditDTO dto = new DentistProfileEditDTO();

        dto.setGender(user.getGender());
        dto.setDateOfBirth(user.getDateOfBirth());

        dto.setFullName(profile.getFullName());
        dto.setPhone(profile.getPhone());
        dto.setSpecialization(profile.getSpecialization());
        dto.setExperienceYears(profile.getExperienceYears());
        dto.setBio(profile.getBio());

        return dto;
    }

    // =========================
    // SAVE PROFILE
    // =========================
    public void updateProfile(String email, DentistProfileEditDTO dto) {

        DentistProfile profile = getProfileByEmail(email);
        User user = profile.getUser();

        // update user
        user.setGender(dto.getGender());
        user.setDateOfBirth(dto.getDateOfBirth());

        // update dentist profile
        profile.setFullName(dto.getFullName());
        profile.setPhone(dto.getPhone());
        profile.setSpecialization(dto.getSpecialization());
        profile.setExperienceYears(dto.getExperienceYears());
        profile.setBio(dto.getBio());

        userRepository.save(user);
        dentistProfileRepository.save(profile);
    }

    // =========================
    // CALCULATE AGE
    // =========================
    public int calculateAge(User user) {
        if (user.getDateOfBirth() == null) return 0;
        return Period.between(user.getDateOfBirth(), java.time.LocalDate.now()).getYears();
    }
}