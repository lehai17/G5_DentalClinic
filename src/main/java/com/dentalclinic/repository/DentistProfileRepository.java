package com.dentalclinic.repository;

import com.dentalclinic.model.profile.DentistProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DentistProfileRepository extends JpaRepository<DentistProfile, Long> {

}
