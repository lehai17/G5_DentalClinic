package com.dentalclinic.repository;

import com.dentalclinic.model.support.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByCustomer_IdOrderByCreatedAtDesc(Long customerId);

    List<SupportTicket> findByStatusOrderByCreatedAtDesc(String status);

    List<SupportTicket> findAllByOrderByCreatedAtDesc();
}
