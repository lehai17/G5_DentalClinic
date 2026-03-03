package com.dentalclinic.controller.admin;

import com.dentalclinic.model.schedule.BusySchedule;
import com.dentalclinic.service.admin.AdminBusyScheduleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/busy-schedule")
public class AdminBusyScheduleController {

    private final AdminBusyScheduleService busyScheduleService;

    public AdminBusyScheduleController(AdminBusyScheduleService busyScheduleService) {
        this.busyScheduleService = busyScheduleService;
    }

    @GetMapping
    public String listRequests(
            @RequestParam(required = false) String dentistName,
            @RequestParam(required = false, defaultValue = "newest") String sort,
            Model model) {

        model.addAttribute("activePage", "busy-schedule");

        // Gọi Service đã sửa lỗi biến
        List<BusySchedule> requests = busyScheduleService.searchAndSortRequests(dentistName, sort);
        model.addAttribute("requests", requests);

        // Trả lại tham số để giữ trạng thái trên UI
        model.addAttribute("dentistName", dentistName);
        model.addAttribute("sort", sort);

        return "admin/busy-schedule-list";
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