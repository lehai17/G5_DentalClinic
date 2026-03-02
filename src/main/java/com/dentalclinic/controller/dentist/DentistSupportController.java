package com.dentalclinic.controller.dentist;

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
        // Lấy thông tin user hiện tại thông qua service đã thống nhất
        User currentUser = supportService.getCurrentUser(principal);
        Long dentistUserId = currentUser.getId();

        // Lấy danh sách phiếu hỗ trợ hiển thị riêng cho bác sĩ này
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

        // Sử dụng hàm đã có logic kiểm tra quyền sở hữu trong Service
        SupportTicket ticket = supportService.getDentistTicketDetail(currentUser.getId(), id);

        model.addAttribute("ticket", ticket);
        return "Dentist/support-detail";
    }

    // ================== TRẢ LỜI PHIẾU HỖ TRỢ ==================
    @PostMapping("/{id}/answer")
    public String answer(@PathVariable Long id,
                         @RequestParam(required = false) String answer,
                         @AuthenticationPrincipal UserDetails principal,
                         RedirectAttributes redirectAttributes) {

        // Kiểm tra nội dung câu trả lời không được trống
        if (answer == null || answer.trim().isEmpty()) {
            redirectAttributes.addFlashAttribute("errorMessage", "Nội dung phản hồi không được để trống.");
            return "redirect:/dentist/support/" + id;
        }

        try {
            User currentUser = supportService.getCurrentUser(principal);

            // Thực hiện lưu câu trả lời vào database
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