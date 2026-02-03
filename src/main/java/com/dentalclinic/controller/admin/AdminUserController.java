package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.DentistDTO;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.service.dentist.DentistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/dentists")
public class AdminUserController {

    @Autowired
    private DentistProfileRepository dentistProfileRepository;

    @Autowired
    private DentistService dentistService;


    // 1. Hiển thị danh sách kèm bộ lọc Tìm kiếm
    @GetMapping
    public String showDentistList(
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam(value = "status", required = false) String status,
            Model model) {

        // Gọi Service xử lý lọc dữ liệu an toàn
        List<DentistProfile> dentists = dentistService.searchDentists(specialty, status);

        model.addAttribute("dentists", dentists);
        model.addAttribute("selectedSpecialty", specialty); // Giữ trạng thái Dropdown
        model.addAttribute("selectedStatus", status);
        model.addAttribute("activePage", "dentists"); // Làm sáng Menu Sidebar

        // Cập nhật số liệu thực tế cho Stat Cards
        model.addAttribute("totalDentists", dentistProfileRepository.count());
        model.addAttribute("onDutyCount", dentistService.countByStatus("ACTIVE"));

        return "admin/dentist-list";
    }

    // 2. Hiển thị Form thêm mới
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("dentistDTO", new DentistDTO());
        model.addAttribute("activePage", "dentists");
        return "admin/add-dentist";
    }

    // 3. Xử lý lưu dữ liệu
    @PostMapping("/save")
    public String processAddDentist(@ModelAttribute("dentistDTO") DentistDTO dto) {
        dentistService.saveDentist(dto);
        return "redirect:/admin/dentists";
    }

    // 4. Xử lý khóa tài khoản bác sĩ
    @PostMapping("/lock/{id}")
    public String lockDentist(@PathVariable("id") Long userId) {
        // Sử dụng chung logic khóa từ hệ thống (trạng thái User sang LOCKED)
        dentistService.deactivateDentist(userId);
        return "redirect:/admin/dentists";
    }
    @PostMapping("/unlock/{id}")
    public String unlockDentist(@PathVariable Long id, RedirectAttributes ra) {
        try {
            // Đã có userService để gọi hàm này
            dentistService.updateDentistStatus(id, UserStatus.ACTIVE);
            ra.addFlashAttribute("success", "Đã mở khóa tài khoản thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/dentists";
    }
}