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
     * Lấy danh sách ticket theo Dentist (userId) thông qua Appointment.dentist.user.
     * Giữ tên method để khớp các service/controller đang gọi.
     */
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
        WHERE a.dentist.user.id = :dentistId
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findByDentistWithAppointment(@Param("dentistId") Long dentistId);

    /**
     * Lấy danh sách phiếu hỗ trợ hiển thị cho Bác sĩ.
     */
    @Query("""
        SELECT s FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.dentist ad
        LEFT JOIN FETCH ad.user
        LEFT JOIN FETCH s.customer c
        LEFT JOIN FETCH c.customerProfile
        LEFT JOIN FETCH s.staff
        WHERE a.dentist.user.id = :dentistUserId
        ORDER BY s.createdAt DESC
    """)
    List<SupportTicket> findVisibleToDentist(@Param("dentistUserId") Long dentistUserId);

    /**
     * Lọc danh sách phiếu hỗ trợ theo trạng thái dành cho Bác sĩ.
     */
    @Query("""
        SELECT s FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.dentist ad
        LEFT JOIN FETCH ad.user
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
     * Xem chi tiết một phiếu hỗ trợ dành cho Bác sĩ (Kiểm tra quyền sở hữu).
     */
    @Query("""
        SELECT s FROM SupportTicket s
        LEFT JOIN FETCH s.appointment a
        LEFT JOIN FETCH a.service
        LEFT JOIN FETCH a.dentist ad
        LEFT JOIN FETCH ad.user
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