package com.dentalclinic.repository;

import com.dentalclinic.model.user.PasswordReset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetRepository extends JpaRepository<PasswordReset, Long> {
    Optional<PasswordReset> findTopByEmailOrderByCreatedAtDesc(String email);
    Optional<PasswordReset> findByToken(String token);
}
