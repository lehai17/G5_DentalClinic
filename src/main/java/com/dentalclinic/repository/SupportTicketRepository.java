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

    // --- Customer Methods ---

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

    // --- Dentist Methods (Optimized with Fetch Joins) ---

    /**
     * Láº¥y danh sÃ¡ch ticket theo Dentist (userId) thÃ´ng qua Appointment.dentist.user.
     * Giá»¯ tÃªn method Ä‘á»ƒ khá»›p cÃ¡c service/controller Ä‘ang gá»i.
     */
    @Query("""
        SELECT DISTINCT s
        FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.appointmentDetails ad
        LEFT JOIN FETCH ad.service
        LEFT JOIN FETCH a.dentist adp
        LEFT JOIN FETCH adp.user
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        LEFT JOIN FETCH s.staff
        WHERE a.dentist.user.id = :dentistId
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findByDentistWithAppointment(@Param("dentistId") Long dentistId);

    /**
     * Láº¥y danh sÃ¡ch phiáº¿u há»— trá»£ hiá»ƒn thá»‹ cho BÃ¡c sÄ©.
     */
    @Query("""
        SELECT DISTINCT s FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.appointmentDetails ad
        LEFT JOIN FETCH ad.service
        LEFT JOIN FETCH a.dentist adp
        LEFT JOIN FETCH adp.user
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        LEFT JOIN FETCH s.staff
        WHERE a.dentist.user.id = :dentistUserId
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findVisibleToDentist(@Param("dentistUserId") Long dentistUserId);

    /**
     * Lá»c danh sÃ¡ch phiáº¿u há»— trá»£ theo tráº¡ng thÃ¡i dÃ nh cho BÃ¡c sÄ©.
     */
    @Query("""
        SELECT DISTINCT s FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.appointmentDetails ad
        LEFT JOIN FETCH ad.service
        LEFT JOIN FETCH a.dentist adp
        LEFT JOIN FETCH adp.user
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        LEFT JOIN FETCH s.staff
        WHERE a.dentist.user.id = :dentistUserId
          AND s.status = :status
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findVisibleToDentistByStatus(
            @Param("dentistUserId") Long dentistUserId,
            @Param("status") SupportStatus status
    );

    /**
     * Xem chi tiáº¿t má»™t phiáº¿u há»— trá»£ dÃ nh cho BÃ¡c sÄ© (Kiá»ƒm tra quyá»n sá»Ÿ há»¯u).
     */
    @Query("""
        SELECT DISTINCT s FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.appointmentDetails ad
        LEFT JOIN FETCH ad.service
        LEFT JOIN FETCH a.dentist adp
        LEFT JOIN FETCH adp.user
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        LEFT JOIN FETCH s.staff
        WHERE s.id = :ticketId
          AND a.dentist.user.id = :dentistUserId
    """)
    Optional<SupportTicket> findVisibleToDentistById(
            @Param("ticketId") Long ticketId,
            @Param("dentistUserId") Long dentistUserId
    );
}
