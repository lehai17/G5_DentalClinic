package com.dentalclinic.repository;

import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.support.SupportStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    @Query("""
        SELECT s
        FROM SupportTicket s
        JOIN FETCH s.appointment a
        JOIN FETCH a.service
        JOIN FETCH s.customer
        WHERE s.dentist.id = :dentistId
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findByDentistWithAppointment(@Param("dentistId") Long dentistId);


    @Query("""
        SELECT s
        FROM SupportTicket s
        JOIN FETCH s.appointment a
        JOIN FETCH a.service
        JOIN FETCH s.customer
        WHERE s.dentist.id = :dentistId
        AND s.status = :status
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findByDentistAndStatusWithAppointment(
            @Param("dentistId") Long dentistId,
            @Param("status") SupportStatus status
    );
}