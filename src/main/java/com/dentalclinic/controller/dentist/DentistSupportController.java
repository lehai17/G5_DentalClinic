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
import org.springframework.web.bind.annotation.*;
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
            bindingResult.rejectValue("answer", "answer.blank", "Vui lòng nhập nội dung phản hồi.");
        }

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nội dung phản hồi không được để trống.");
            return "redirect:/dentist/support/" + id;
        }

        Long dentistUserId = supportService.getCurrentUser(principal).getId();
        supportService.answerTicket(dentistUserId, id, form.getAnswer());
        redirectAttributes.addFlashAttribute("successMessage", "Đã phản hồi phiếu hỗ trợ thành công.");
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
