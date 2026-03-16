package com.dentalclinic.repository;

import com.dentalclinic.model.review.Review;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    Optional<Review> findByAppointment_Id(Long appointmentId);

    Optional<Review> findByAppointment_IdAndCustomer_User_Id(Long appointmentId, Long customerUserId);

    boolean existsByAppointment_Id(Long appointmentId);
}
