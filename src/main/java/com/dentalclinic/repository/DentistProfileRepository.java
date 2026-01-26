package com.dentalclinic.repository;

import com.dentalclinic.model.profile.DentistProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DentistProfileRepository extends JpaRepository<DentistProfile, Long> {

    // Find dentist by specialization
    List<DentistProfile> findBySpecialization(String specialization);

    // Find dentists with minimum experience
    List<DentistProfile> findByExperienceYearsGreaterThanEqual(int years);

    // Check dentist profile exists
    boolean existsByUser_Id(Long userId);
}
