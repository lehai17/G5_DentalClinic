package com.dentalclinic.repository;

import com.dentalclinic.model.payment.BillingNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BillingNoteRepository extends JpaRepository<BillingNote, Long> {

    Optional<BillingNote> findByAppointment_Id(Long appointmentId);

    @Query("""
        SELECT DISTINCT bn
        FROM BillingNote bn
        LEFT JOIN FETCH bn.performedServices ps
        LEFT JOIN FETCH ps.service
        WHERE bn.appointment.id = :appointmentId
    """)
    Optional<BillingNote> findByAppointment_IdWithPerformedServices(@Param("appointmentId") Long appointmentId);

    Optional<BillingNote> findByAppointment_IdAndAppointment_Customer_User_Id(
            Long appointmentId,
            Long customerUserId
    );

    @Query("""
        SELECT DISTINCT bn FROM BillingNote bn
        LEFT JOIN FETCH bn.prescriptionItems
        WHERE bn.appointment.id IN :appointmentIds
    """)
    List<BillingNote> findByAppointment_IdInWithPrescriptions(@Param("appointmentIds") List<Long> appointmentIds);
}
