package com.dentalclinic.controller.admin;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.ui.Model;

/**
 * Controller xử lý cï¿½c yêu cầu liên quan đến m� n hình Dashboard chính của Admin.
 * Được phï¿½n tï¿½ch v� o sub-package 'admin' để quản lý độc lập.
 */
@Controller
@RequestMapping("/admin") // Tiền tố URL cho to� n bộ chức năng quản trị
public class AdminDashboardController {
    /**
     * Hiển thị trang Dashboard chính của GENZ CLINIC.
     * URL truy cập: http://localhost:8080/admin/dashboard
     */
    @GetMapping("/dashboard")
    public String showDashboard(Model model) {
        // Bạn có thể thêm cï¿½c thông tin động v� o model tại dï¿½y trong tương lai
        model.addAttribute("pageTitle", "Admin Dashboard - GENZ CLINIC");
        model.addAttribute("adminName", "Administrator"); // Giả lập tên admin từ Role

        // Trả về file HTML nằm tại resources/templates/admin/admindashboard.html
        return "admin/admindashboard";
    }

    @GetMapping("/admin/dashboard")
    public String adminDashboard(Model model) {
        model.addAttribute("activePage", "dashboard");
        return "admin/admindashboard";
    }


    // Bạn có thể thêm cï¿½c Route khï¿½c cho phï¿½n hệ Dashboard tại dï¿½y nếu cần
}


