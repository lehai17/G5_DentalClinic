package com.dentalclinic.controller.admin;

import com.dentalclinic.service.admin.AdminBusyScheduleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/busy-schedule")
public class AdminBusyScheduleController {

    private final AdminBusyScheduleService busyScheduleService;

    public AdminBusyScheduleController(AdminBusyScheduleService busyScheduleService) {
        this.busyScheduleService = busyScheduleService;
    }

    @GetMapping
    public String listRequests(Model model) {
        model.addAttribute("activePage", "busy-schedule");
        model.addAttribute("requests", busyScheduleService.getAllRequests());
        return "admin/busy-schedule-list"; // Tên file HTML bên dưới
    }

    @PostMapping("/approve")
    public String approveRequest(@RequestParam Long id, RedirectAttributes ra) {
        try {
            busyScheduleService.updateStatus(id, "APPROVED");
            ra.addFlashAttribute("message", "Đã phê duyệt đơn nghỉ thành công.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/busy-schedule";
    }

    @PostMapping("/reject")
    public String rejectRequest(@RequestParam Long id, RedirectAttributes ra) {
        try {
            busyScheduleService.updateStatus(id, "REJECTED");
            ra.addFlashAttribute("message", "Đã từ chối đơn nghỉ.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/busy-schedule";
    }
}