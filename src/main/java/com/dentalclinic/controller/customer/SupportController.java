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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public String createPage(Model model, @AuthenticationPrincipal UserDetails principal) {
        User currentUser = supportService.getCurrentUser(principal);
        if (!model.containsAttribute("form")) {
            model.addAttribute("form", new TicketCreateForm());
        }
        if (!model.containsAttribute("appointments")) {
            model.addAttribute("appointments", supportService.getAppointmentsForSupport(currentUser.getId()));
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
        if (form.getTitle() == null || form.getTitle().trim().isEmpty()) {
            bindingResult.rejectValue("title", "title.blank", "Vui lòng nhập tiêu đề.");
        }
        if (form.getQuestion() == null || form.getQuestion().trim().isEmpty()) {
            bindingResult.rejectValue("question", "question.blank", "Vui lòng nhập câu hỏi.");
        }

        User currentUser = supportService.getCurrentUser(principal);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.form", bindingResult);
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute("appointments", supportService.getAppointmentsForSupport(currentUser.getId()));
            return "redirect:/support/create";
        }

        try {
            supportService.createTicket(currentUser.getId(), form.getAppointmentId(), form.getTitle(), form.getQuestion());
            redirectAttributes.addFlashAttribute("successMessage", "Gửi phiếu hỗ trợ th� nh công.");
            return "redirect:/support/my";
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("form", form);
            redirectAttributes.addFlashAttribute("appointments", supportService.getAppointmentsForSupport(currentUser.getId()));
            return "redirect:/support/create";
        }
    }

    @PostMapping("/{id}/reply")
    public String replyTicket(@PathVariable Long id,
                              @ModelAttribute("replyForm") TicketReplyForm replyForm,
                              BindingResult bindingResult,
                              @AuthenticationPrincipal UserDetails principal,
                              RedirectAttributes redirectAttributes) {
        if (replyForm == null || replyForm.getMessage() == null || replyForm.getMessage().trim().isEmpty()) {
            bindingResult.rejectValue("message", "message.blank", "Vui lòng nhập nội dung phản hồi.");
        }

        User currentUser = supportService.getCurrentUser(principal);
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nội dung phản hồi không được để trống.");
            return "redirect:/support/" + id;
        }

        supportService.replyTicket(currentUser.getId(), id, replyForm.getMessage());
        redirectAttributes.addFlashAttribute("successMessage", "Đã gửi phản hồi của bạn.");
        return "redirect:/support/" + id;
    }

    @GetMapping("/my")
    public String myTickets(@AuthenticationPrincipal UserDetails principal,
                            @RequestParam(defaultValue = "0") int page,
                            @RequestParam(required = false) String keyword,
                            @RequestParam(required = false, defaultValue = "newest") String sort,
                            Model model) {
        User currentUser = supportService.getCurrentUser(principal);
        int pageSize = 5;
        var ticketsPage = supportService.getMyTicketsPage(currentUser.getId(), page, pageSize, keyword, sort);
        model.addAttribute("tickets", ticketsPage.getContent());
        model.addAttribute("currentPage", ticketsPage.getNumber());
        model.addAttribute("totalPages", ticketsPage.getTotalPages());
        model.addAttribute("keyword", keyword == null ? "" : keyword.trim());
        model.addAttribute("selectedSort", sort == null ? "newest" : sort.trim());
        model.addAttribute("active", "support");
        return "customer/support-my";
    }

    @GetMapping("/{id}")
    public String ticketDetail(@PathVariable Long id,
                               @AuthenticationPrincipal UserDetails principal,
                               Model model) {
        User currentUser = supportService.getCurrentUser(principal);
        SupportTicket ticket = supportService.getTicketDetail(currentUser.getId(), id);
        model.addAttribute("ticket", ticket);
        if (!model.containsAttribute("replyForm")) {
            model.addAttribute("replyForm", new TicketReplyForm());
        }
        model.addAttribute("active", "support");
        return "customer/support-detail";
    }

    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    public String handleBusinessError(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/support/my";
    }

    public static class TicketCreateForm {
        private Long appointmentId;

        @NotBlank
        private String title;

        @NotBlank
        private String question;

        public Long getAppointmentId() {
            return appointmentId;
        }

        public void setAppointmentId(Long appointmentId) {
            this.appointmentId = appointmentId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getQuestion() {
            return question;
        }

        public void setQuestion(String question) {
            this.question = question;
        }
    }

    public static class TicketReplyForm {
        @NotBlank
        private String message;

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

