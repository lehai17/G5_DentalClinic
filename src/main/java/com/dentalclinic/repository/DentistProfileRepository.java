package com.dentalclinic.repository;

import com.dentalclinic.model.profile.DentistProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DentistProfileRepository extends JpaRepository<DentistProfile, Long> {

    // dùng trong toàn bộ flow hiện tại
    Optional<DentistProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);
}
