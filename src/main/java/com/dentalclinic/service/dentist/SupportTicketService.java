package com.dentalclinic.service.dentist;

import com.dentalclinic.model.support.SupportStatus;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.repository.SupportTicketRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SupportTicketService {

    private final SupportTicketRepository supportTicketRepository;

    public SupportTicketService(SupportTicketRepository supportTicketRepository) {
        this.supportTicketRepository = supportTicketRepository;
    }

    public List<SupportTicket> getTicketsByDentist(Long dentistId) {
        return supportTicketRepository.findByDentistWithAppointment(dentistId);
    }

    public SupportTicket getById(Long id) {
        return supportTicketRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Ticket not found"));
    }

    public void answerTicket(Long ticketId, String answer) {
        SupportTicket ticket = getById(ticketId);

        if (ticket.getStatus() == SupportStatus.CLOSED) {
            throw new IllegalStateException("Ticket already closed");
        }

        ticket.setAnswer(answer);
        ticket.setStatus(SupportStatus.CLOSED);

        supportTicketRepository.save(ticket);
    }
}