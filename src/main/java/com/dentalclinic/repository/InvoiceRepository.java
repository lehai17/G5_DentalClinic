package com.dentalclinic.repository;

import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @EntityGraph(attributePaths = {
            "appointment",
            "appointment.customer",
            "appointment.customer.user",
            "appointment.dentist",
            "appointment.service",
            "appointment.appointmentDetails",
            "appointment.appointmentDetails.service",
            "voucher"
    })
    Optional<Invoice> findByAppointment_Id(Long appointmentId);
    Optional<Invoice> findByAppointment_IdAndAppointment_Customer_User_Id(Long appointmentId, Long customerUserId);
    boolean existsByAppointment_IdAndStatus(Long appointmentId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Invoice i WHERE i.id = :invoiceId")
    Optional<Invoice> findByIdForUpdate(@Param("invoiceId") Long invoiceId);

    @EntityGraph(attributePaths = {
            "appointment",
            "appointment.customer",
            "appointment.customer.user",
            "appointment.dentist",
            "appointment.service",
            "appointment.appointmentDetails",
            "appointment.appointmentDetails.service"
    })
    List<Invoice> findByAppointment_Customer_User_IdOrderByCreatedAtDesc(Long customerUserId);

    @EntityGraph(attributePaths = {
            "appointment",
            "appointment.customer",
            "appointment.customer.user",
            "appointment.dentist",
            "appointment.service",
            "appointment.appointmentDetails",
            "appointment.appointmentDetails.service"
    })
    Optional<Invoice> findByIdAndAppointment_Customer_User_Id(Long invoiceId, Long customerUserId);
}
