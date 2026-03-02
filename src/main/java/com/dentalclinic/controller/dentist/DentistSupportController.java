package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.dentist.SupportTicketService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/dentist/support")
public class DentistSupportController {

    private final SupportTicketService supportTicketService;
    private final UserRepository userRepository;

    public DentistSupportController(
            SupportTicketService supportTicketService,
            UserRepository userRepository
    ) {
        this.supportTicketService = supportTicketService;
        this.userRepository = userRepository;
    }

    // ================== DANH SÁCH ==================
    @GetMapping
    public String list(Authentication authentication, Model model) {

        Long dentistId = getCurrentUserId(authentication);

        List<SupportTicket> tickets =
                supportTicketService.getTicketsByDentist(dentistId);

        model.addAttribute("tickets", tickets);

        return "Dentist/support-list";
    }

    // ================== CHI TIẾT ==================
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         Authentication authentication,
                         Model model) {

        SupportTicket ticket = supportTicketService.getById(id);

        Long dentistId = getCurrentUserId(authentication);

        if (!ticket.getDentist().getId().equals(dentistId)) {
            throw new IllegalStateException("Unauthorized access");
        }

        model.addAttribute("ticket", ticket);

        return "Dentist/support-detail";
    }

    // ================== TRẢ LỜI ==================
    @PostMapping("/{id}/answer")
    public String answer(@PathVariable Long id,
                         @RequestParam String answer,
                         Authentication authentication) {

        SupportTicket ticket = supportTicketService.getById(id);

        Long dentistId = getCurrentUserId(authentication);

        if (!ticket.getDentist().getId().equals(dentistId)) {
            throw new IllegalStateException("Unauthorized action");
        }

        supportTicketService.answerTicket(id, answer);

        return "redirect:/dentist/support";
    }

    // ================== LẤY USER HIỆN TẠI ==================
    private Long getCurrentUserId(Authentication authentication) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        return user.getId();
    }
}