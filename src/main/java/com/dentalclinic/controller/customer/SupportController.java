package com.dentalclinic.controller.customer;

import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.User;
import com.dentalclinic.service.support.SupportService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/support")
@PreAuthorize("hasRole('CUSTOMER')")
public class SupportController {

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping("/create")
    public String createPage(Model model) {
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TicketCreateForm());
        }
        model.addAttribute("active", "support");
        return "customer/support-create";
    }

    @PostMapping("/create")
    public String createTicket(@ModelAttribute("form") TicketCreateForm form,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal UserDetails principal,
                               RedirectAttributes redirectAttributes) {
        if (form == null) {
            form = new TicketCreateForm();
        }
        if (form.getQuestion() == null || form.getQuestion().trim().isEmpty()) {
            bindingResult.rejectValue("question", "question.blank", "Vui lòng nhập câu hỏi.");
        }
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            return "redirect:/support/create";
        }

        User currentUser = supportService.getCurrentUser(principal);
        supportService.createTicket(currentUser.getId(), form.getQuestion());
        redirectAttributes.addFlashAttribute("successMessage", "Gửi phiếu hỗ trợ thành công.");
        return "redirect:/support/my";
    }

    @GetMapping("/my")
    public String myTickets(@AuthenticationPrincipal UserDetails principal, Model model) {
        User currentUser = supportService.getCurrentUser(principal);
        model.addAttribute("tickets", supportService.getMyTickets(currentUser.getId()));
        model.addAttribute("active", "support");
        return "customer/support-my";
    }

    @GetMapping("/{id}")
    public String ticketDetail(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails principal,
                               RedirectAttributes redirectAttributes,
                               Model model) {
        User currentUser = supportService.getCurrentUser(principal);
        SupportTicket ticket = supportService.getTicketDetail(currentUser.getId(), id);
        model.addAttribute("ticket", ticket);
        model.addAttribute("active", "support");
        return "customer/support-detail";
    }

    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    public String handleBusinessError(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/support/my";
    }

    public static class TicketCreateForm {
        @NotBlank
        private String question;

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }
}
