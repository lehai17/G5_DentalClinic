package com.dentalclinic.repository;

import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.support.SupportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    // --- Nhóm các hàm cho Customer ---
    List<SupportTicket> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();

    // Sửa lỗi kiểu dữ liệu: Repository nên nhận SupportStatus (Enum) thay vì String
    // để khớp với kiểu dữ liệu trong Model SupportTicket
    List<SupportTicket> findByStatusOrderByCreatedAtDesc(SupportStatus status);

    // --- Nhóm các hàm cho Dentist (Sử dụng Query để tối ưu Fetching) ---
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