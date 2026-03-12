package com.dentalclinic.repository;

import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    Optional<Invoice> findByAppointment_Id(Long appointmentId);
    Optional<Invoice> findByAppointment_IdAndAppointment_Customer_User_Id(Long appointmentId, Long customerUserId);
    boolean existsByAppointment_IdAndStatus(Long appointmentId, PaymentStatus status);
}
