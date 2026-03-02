package com.dentalclinic.controller.dentist;

import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.User;
import com.dentalclinic.service.support.SupportService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/dentist/support")
@PreAuthorize("hasRole('DENTIST')") // Chỉ cho phép người dùng có quyền DENTIST truy cập
public class DentistSupportController {

    private final SupportService supportService;

    public DentistSupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    // ================== DANH SÁCH PHIẾU HỖ TRỢ ==================
    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @AuthenticationPrincipal UserDetails principal,
                       Model model) {

        User currentUser = supportService.getCurrentUser(principal);
        Long dentistUserId = currentUser.getId();

        List<SupportTicket> tickets = supportService.getDentistVisibleTickets(dentistUserId, status);

        model.addAttribute("tickets", tickets);
        model.addAttribute("selectedStatus", status == null ? "" : status.trim().toUpperCase());

        return "Dentist/support-list";
    }

    // ================== CHI TIẾT PHIẾU HỖ TRỢ ==================
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         Model model) {

        User currentUser = supportService.getCurrentUser(principal);

        // Service chịu trách nhiệm check quyền/visibility
        SupportTicket ticket = supportService.getDentistTicketDetail(currentUser.getId(), id);

        model.addAttribute("ticket", ticket);
        return "Dentist/support-detail";
    }

    // ================== TRẢ LỜI PHIẾU HỖ TRỢ ==================
    @PostMapping("/{id}/answer")
    public String answer(@PathVariable Long id,
                         @RequestParam(name = "answer", required = false) String answer,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes redirectAttributes) {

        // Validate: câu trả lời không được trống
        if (answer == null || answer.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nội dung phản hồi không được để trống.");
            return "redirect:/dentist/support/" + id;
        }

        try {
            User currentUser = supportService.getCurrentUser(principal);

            // Lưu câu trả lời
            supportService.answerTicket(currentUser.getId(), id, answer.trim());

            redirectAttributes.addFlashAttribute("successMessage", "Đã gửi phản hồi thành công.");
        } catch (BusinessException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }

        return "redirect:/dentist/support/" + id;
    }

    // ================== XỬ LÝ LỖI TẬP TRUNG ==================
    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class, IllegalStateException.class})
    public String handleBusinessError(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/dentist/support";
    }
}