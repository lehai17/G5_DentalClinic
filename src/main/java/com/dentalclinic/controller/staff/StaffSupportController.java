package com.dentalclinic.controller.staff;

import com.dentalclinic.exception.BusinessException;
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
@RequestMapping("/staff/support")
@PreAuthorize("hasRole('STAFF')")
public class StaffSupportController {

    private final SupportService supportService;

    public StaffSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    @GetMapping
    public String supportTickets(@RequestParam(required = false) String status,
                                 @AuthenticationPrincipal UserDetails principal,
                                 Model model) {
        Long currentUserId = supportService.getCurrentUser(principal).getId();
        model.addAttribute("tickets", supportService.getAllTickets(status, currentUserId));
        model.addAttribute("selectedStatus", status == null ? "" : status.trim().toUpperCase());
        return "staff/support-tickets";
    }

    @PostMapping("/{id}/answer")
    public String answerTicket(@PathVariable Long id,
                               @ModelAttribute("form") TicketAnswerForm form,
                               BindingResult bindingResult,
                               @AuthenticationPrincipal UserDetails principal,
                               RedirectAttributes redirectAttributes) {
        if (form == null || form.getAnswer() == null || form.getAnswer().trim().isEmpty()) {
            bindingResult.rejectValue("answer", "answer.blank", "Vui lòng nhập câu trả lời.");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nội dung trả lời không được để trống.");
            return "redirect:/staff/support";
        }

        Long currentUserId = supportService.getCurrentUser(principal).getId();
        supportService.answerTicket(currentUserId, id, form.getAnswer());
        redirectAttributes.addFlashAttribute("successMessage", "Trả lời phiếu hỗ trợ thành công.");
        return "redirect:/staff/support";
    }

    @PostMapping("/{id}/close")
    public String closeTicket(@PathVariable Long id,
                              @AuthenticationPrincipal UserDetails principal,
                              RedirectAttributes redirectAttributes) {
        Long currentUserId = supportService.getCurrentUser(principal).getId();
        supportService.closeTicket(currentUserId, id);
        redirectAttributes.addFlashAttribute("successMessage", "Đã đóng phiếu hỗ trợ.");
        return "redirect:/staff/support";
    }

    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    public String handleBusinessError(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/staff/support";
    }

    public static class TicketAnswerForm {
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
