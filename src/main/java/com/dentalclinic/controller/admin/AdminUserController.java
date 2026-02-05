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
    public String processAddDentist(@ModelAttribute("dentistDTO") DentistDTO dto, RedirectAttributes ra) {
        try {
            dentistService.saveDentist(dto);
            ra.addFlashAttribute("success", "Thêm bác sĩ thành công!");
            return "redirect:/admin/dentists";
        } catch (RuntimeException e) {
            // Gửi thông báo lỗi quay lại màn hình Add
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/dentists/add";
        }
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
    @GetMapping("/detail/{id}")
    public String showDentistDetail(@PathVariable("id") Long id, Model model) {
        // Log ra để kiểm tra xem request đã vào tới đây chưa
        System.out.println("Đang xem chi tiết bác sĩ có ID: " + id);

        DentistDTO dentist = dentistService.getDentistById(id);
        model.addAttribute("dentist", dentist);

        // Trả về file HTML tại: src/main/resources/templates/admin/dentist-detail.html
        return "admin/dentist-detail";
    }
//    @GetMapping("/admin/dentists")
//    public String listDentists(@RequestParam(value = "keyword", required = false) String keyword, Model model) {
//        // Gọi hàm search mới từ Service
//        List<DentistDTO> dentists = dentistService.searchByKeyword(keyword);
//
//        model.addAttribute("dentists", dentists);
//
//        // BẮT BUỘC: Gửi keyword về View để ô input giữ lại nội dung đã gõ
//        model.addAttribute("keyword", keyword != null ? keyword : "");
//
//        // (Các logic đếm số lượng Total, On Duty... vẫn giữ nguyên)
//        return "admin/dentist-list";
//    }
//    @GetMapping("/admin/dentists/api/search")
//    public String searchApi(@RequestParam String keyword, Model model) {
//        // Phải nạp vào biến mang tên "dentists"
//        List<DentistDTO> dentists = dentistService.searchByKeyword(keyword);
//        model.addAttribute("dentists", dentists);
//
//        return "admin/fragments/dentist-table :: dentist-list";
//    }
}