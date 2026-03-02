package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.admin.StaffDTO;
import com.dentalclinic.model.profile.StaffProfile;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.StaffProfileRepository;
import com.dentalclinic.service.staff.StaffService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/staff")
public class AdminStaffController {

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
        model.addAttribute("activePage", "staff");

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
    public String processAddStaff(@jakarta.validation.Valid @ModelAttribute("staffDTO") StaffDTO dto,
                                  org.springframework.validation.BindingResult bindingResult, Model model, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("activePage", "staff");
            return "admin/add-staff";
        }
        try {
            staffService.saveStaff(dto);
            ra.addFlashAttribute("success", "Thêm nhân viên thành công!");
            return "redirect:/admin/staff";
        } catch (RuntimeException e) {
            // Giữ lại form và hiển thị lỗi
            model.addAttribute("error", e.getMessage());
            model.addAttribute("activePage", "staff");
            return "admin/add-staff";
        }
    }

    @PostMapping("/lock/{id}")
    public String lockStaff(@PathVariable("id") Long userId, RedirectAttributes ra) {
        // userId này là ID của bảng User để khớp với hàm trong Service của bạn
        staffService.deactivateStaff(userId);
        ra.addFlashAttribute("success", "Đã khóa nhân viên thành công!");
        return "redirect:/admin/staff";
    }

    @PostMapping("/unlock/{id}")
    public String unlockStaff(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            // Gọi hàm dùng chung để đưa trạng thái về ACTIVE
            staffService.updateStaffStatus(id, UserStatus.ACTIVE);
            ra.addFlashAttribute("success", "Đã mở khóa nhân viên thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    @GetMapping("/detail/{id}")
    public String showStaffDetail(@PathVariable("id") Long id, Model model) {
        com.dentalclinic.dto.admin.UpdateStaffDTO staff = staffService.getStaffForUpdate(id);
        model.addAttribute("staff", staff);
        model.addAttribute("activePage", "staff");
        return "admin/staff-detail";
    }

    @GetMapping("/api/get/{id}")
    @ResponseBody
    public com.dentalclinic.dto.admin.UpdateStaffDTO getStaffData(@PathVariable("id") Long id) {
        return staffService.getStaffForUpdate(id);
    }

    @PostMapping("/update/{id}")
    public String updateStaff(@PathVariable("id") Long id,
                              @jakarta.validation.Valid @ModelAttribute("updateStaffDTO") com.dentalclinic.dto.admin.UpdateStaffDTO dto,
                              org.springframework.validation.BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", "Kiểm tra lại thông tin cập nhật (có thể do lỗi định dạng).");
            return "redirect:/admin/staff";
        }
        try {
            staffService.updateStaff(id, dto);
            ra.addFlashAttribute("success", "Cập nhật nhân viên thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/staff";
    }

    @DeleteMapping("/api/delete/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> deleteStaff(@PathVariable("id") Long id) {
        try {
            staffService.deleteStaff(id);
            return org.springframework.http.ResponseEntity.ok()
                    .body(java.util.Map.of("success", true, "message", "Xóa nhân viên thành công!"));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }
}