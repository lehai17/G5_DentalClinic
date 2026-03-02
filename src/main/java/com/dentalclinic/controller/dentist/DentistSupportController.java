package com.dentalclinic.controller.dentist;

import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.service.support.SupportService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/dentist/support")
@PreAuthorize("hasRole('DENTIST')")
public class DentistSupportController {

    private final SupportService supportService;

    public DentistSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {
        Long dentistUserId = supportService.getCurrentUser(principal).getId();
        model.addAttribute("tickets", supportService.getDentistVisibleTickets(dentistUserId, status));
        model.addAttribute("selectedStatus", status == null ? "" : status.trim().toUpperCase());
        return "Dentist/support-list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {
        Long dentistUserId = supportService.getCurrentUser(principal).getId();
        SupportTicket ticket = supportService.getDentistTicketDetail(dentistUserId, id);
        model.addAttribute("ticket", ticket);
        return "Dentist/support-detail";
    }

    @PostMapping("/{id}/answer")
    public String answer(@PathVariable Long id,
                         @ModelAttribute("form") DentistAnswerForm form,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes redirectAttributes) {
        if (form == null || form.getAnswer() == null || form.getAnswer().trim().isEmpty()) {
            bindingResult.rejectValue("answer", "answer.blank", "Vui long nhap noi dung phan hoi.");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Noi dung phan hoi khong duoc de trong.");
            return "redirect:/dentist/support/" + id;
        }

        Long dentistUserId = supportService.getCurrentUser(principal).getId();
        supportService.answerTicket(dentistUserId, id, form.getAnswer());
        redirectAttributes.addFlashAttribute("successMessage", "Da phan hoi phieu ho tro thanh cong.");
        return "redirect:/dentist/support/" + id;
    }

    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    public String handleBusinessError(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/dentist/support";
    }

    public static class DentistAnswerForm {
        @NotBlank
        private String answer;

        public String getAnswer() {
            return answer;
        }

        public void setAnswer(String answer) {
            this.answer = answer;
        }
    }
}
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
