package com.dentalclinic.controller.admin;

import com.dentalclinic.service.admin.AdminDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        model.addAttribute("pageTitle", "Tổng quan - GENZ CLINIC");
        model.addAttribute("stats", adminDashboardService.getDashboardStats());
        model.addAttribute("activePage", "dashboard");
        return "admin/admindashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        return "redirect:/admin/dashboard";
    }
}
