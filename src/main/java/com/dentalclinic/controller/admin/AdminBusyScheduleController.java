package com.dentalclinic.controller.admin;

import com.dentalclinic.service.admin.AdminBusyScheduleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
        return "admin/busy-schedule-list";
    }

    @PostMapping("/approve")
    public String approveRequest(@RequestParam Long id) {
        busyScheduleService.updateStatus(id, "APPROVED");
        return "redirect:/admin/busy-schedule?approved=true";
    }

    @PostMapping("/reject")
    public String rejectRequest(@RequestParam Long id) {
        busyScheduleService.updateStatus(id, "REJECTED");
        return "redirect:/admin/busy-schedule?rejected=true";
    }
}