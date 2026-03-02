package com.dentalclinic.repository;

import com.dentalclinic.model.support.SupportStatus;
import com.dentalclinic.model.support.SupportTicket;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    @EntityGraph(attributePaths = {"appointment", "staff", "customer"})
    List<SupportTicket> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"appointment", "staff", "customer"})
    Page<SupportTicket> findByCustomer_Id(Long customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"appointment", "staff", "customer"})
    Optional<SupportTicket> findFirstByCustomer_IdAndAnswerIsNotNullOrderByCreatedAtDesc(Long customerId);

    @EntityGraph(attributePaths = {"appointment", "staff", "customer"})
    List<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportStatus status);

    @EntityGraph(attributePaths = {"appointment", "staff", "customer"})
    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    @Query("""
        SELECT s
        FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        WHERE s.staff.id = :dentistId
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findByDentistWithAppointment(@Param("dentistId") Long dentistId);

    @Query("""
        SELECT s
        FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        WHERE s.staff.id = :dentistId
          AND s.status = :status
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findByDentistAndStatusWithAppointment(
            @Param("dentistId") Long dentistId,
            @Param("status") SupportStatus status
    );

    @Query("""
        SELECT s
        FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.dentist ad
        LEFT JOIN FETCH ad.user
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        LEFT JOIN FETCH s.staff
        WHERE a IS NULL OR ad.user.id = :dentistUserId
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findVisibleToDentist(@Param("dentistUserId") Long dentistUserId);

    @Query("""
        SELECT s
        FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.dentist ad
        LEFT JOIN FETCH ad.user
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        LEFT JOIN FETCH s.staff
        WHERE (a IS NULL OR ad.user.id = :dentistUserId)
          AND s.status = :status
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findVisibleToDentistByStatus(@Param("dentistUserId") Long dentistUserId,
                                                      @Param("status") SupportStatus status);

    @Query("""
        SELECT s
        FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.dentist ad
        LEFT JOIN FETCH ad.user
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        LEFT JOIN FETCH s.staff
        WHERE s.id = :ticketId
          AND (a IS NULL OR ad.user.id = :dentistUserId)
    """)
    Optional<SupportTicket> findVisibleToDentistById(@Param("ticketId") Long ticketId,
                                                      @Param("dentistUserId") Long dentistUserId);
}
