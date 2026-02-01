package com.dentalclinic.repository;

import com.dentalclinic.model.payment.BillingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface BillingNoteRepository extends JpaRepository<BillingNote, Long> {

    Optional<BillingNote> findByAppointment_Id(Long appointmentId);

    Optional<BillingNote> findByAppointment_IdAndAppointment_Customer_User_Id(
            Long appointmentId,
            Long customerUserId
    );
}
