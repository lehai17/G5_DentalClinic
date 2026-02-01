package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.StaffDTO;
import com.dentalclinic.model.profile.StaffProfile;
import com.dentalclinic.repository.StaffProfileRepository;
import com.dentalclinic.service.staff.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/staff")
public class AdminStaffController {

    @Autowired
    private StaffProfileRepository staffProfileRepository;

    @Autowired
    private StaffService staffService;

    // 1. Hiển thị danh sách nhân viên kèm bộ lọc
    @GetMapping
    public String showStaffList(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "position", required = false) String position,
            Model model) {

        // Gọi hàm lọc an toàn từ Service đã xử lý Enum
        List<StaffProfile> staffs = staffService.searchStaffs(status, position);

        model.addAttribute("staffs", staffs);
        model.addAttribute("selectedStatus", status); // Giữ trạng thái dropdown sau khi lọc
        model.addAttribute("selectedPos", position);
        model.addAttribute("activePage", "staff"); // Làm sáng menu Sidebar

        // Cập nhật dữ liệu cho các thẻ thống kê (Stat Cards)
        model.addAttribute("totalStaff", staffService.countTotal());
        model.addAttribute("lockedStaffCount", staffService.countByStatus("LOCKED"));
        model.addAttribute("activeStaffCount", staffService.countByStatus("ACTIVE"));

        return "admin/staff-list";
    }

    // 2. Hiển thị Form thêm mới nhân viên
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("staffDTO", new StaffDTO());
        model.addAttribute("activePage", "staff");
        return "admin/add-staff";
    }

    // 3. Xử lý lưu dữ liệu
    @PostMapping("/save")
    public String processAddStaff(@ModelAttribute("staffDTO") StaffDTO dto) {
        staffService.saveStaff(dto);
        return "redirect:/admin/staff";
    }

    // 4. Xử lý khóa tài khoản nhân viên
    @PostMapping("/lock/{id}")
    public String lockStaff(@PathVariable("id") Long userId) {
        // userId này là ID của bảng User để khớp với hàm trong Service của bạn
        staffService.deactivateStaff(userId);
        return "redirect:/admin/staff";
    }
}