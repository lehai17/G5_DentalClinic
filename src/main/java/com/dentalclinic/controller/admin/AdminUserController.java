package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.admin.DentistDTO;
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

    // 1. Hiển thị danh sï¿½ch kèm bộ lọc Tìm kiếm
    @GetMapping
    public String showDentistList(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam(value = "status", required = false) String status,
            Model model) {

        // Gọi Service xử lý lọc dữ liệu an to n
        List<DentistProfile> dentists = dentistService.searchDentists(keyword, specialty, status);

        model.addAttribute("dentists", dentists);
        model.addAttribute("keyword", keyword);
        model.addAttribute("selectedSpecialty", specialty); // Giữ trạng thï¿½i Dropdown
        model.addAttribute("selectedStatus", status);
        model.addAttribute("activePage", "dentists"); // L m sï¿½ng Menu Sidebar

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
    public String processAddDentist(@jakarta.validation.Valid @ModelAttribute("dentistDTO") DentistDTO dto,
            org.springframework.validation.BindingResult bindingResult, Model model, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("activePage", "dentists");
            return "admin/add-dentist";
        }
        try {
            dentistService.saveDentist(dto);
            ra.addFlashAttribute("success", "Thêm bác sĩ thành công!");
            return "redirect:/admin/dentists";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("activePage", "dentists");
            return "admin/add-dentist";
        }
    }

    @PostMapping("/lock/{id}")
    public String lockDentist(@PathVariable("id") Long userId, RedirectAttributes ra) {
        // Sử dụng chung logic khóa từ hệ thống (trạng thï¿½i User sang LOCKED)
        dentistService.deactivateDentist(userId);
        ra.addFlashAttribute("success", "Khóa tài khoản bác sĩ thành công!");
        return "redirect:/admin/dentists";
    }

    @PostMapping("/unlock/{id}")
    public String unlockDentist(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            // ï¿½ï¿½ có userService để gọi h� m n� y
            dentistService.updateDentistStatus(id, UserStatus.ACTIVE);
            ra.addFlashAttribute("success", "Mở khóa bác sĩ thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/dentists";
    }

    @GetMapping("/detail/{id}")
    public String showDentistDetail(@PathVariable("id") Long id, Model model) {
        // Log ra để kiểm tra xem request dï¿½ v� o tới dï¿½y chưa
        System.out.println(" xem chi tiết bác sĩ có ID: " + id);

        DentistDTO dentist = dentistService.getDentistById(id);
        model.addAttribute("dentist", dentist);

        // Trả về file HTML tại: src/main/resources/templates/admin/dentist-detail.html
        return "admin/dentist-detail";
    }

    @GetMapping("/api/search")
    public String searchApi(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "specialty", required = false) String specialty,
            @RequestParam(value = "status", required = false) String status,
            Model model) {
        List<com.dentalclinic.model.profile.DentistProfile> dentists = dentistService.searchDentists(keyword, specialty,
                status);
        model.addAttribute("dentists", dentists);
        return "admin/fragments/dentist-table :: dentist-list";
    }

    @GetMapping("/api/get/{id}")
    @ResponseBody
    public com.dentalclinic.dto.admin.UpdateDentistDTO getDentistData(@PathVariable("id") Long id) {
        return dentistService.getDentistForUpdate(id);
    }

    @PostMapping("/update/{id}")
    public String updateDentist(@PathVariable("id") Long id,
            @jakarta.validation.Valid @ModelAttribute("updateDentistDTO") com.dentalclinic.dto.admin.UpdateDentistDTO dto,
            org.springframework.validation.BindingResult bindingResult, RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            ra.addFlashAttribute("error", "Vui lòng kiểm tra lại thông tin điền, sai định dạng!");
            return "redirect:/admin/dentists";
        }
        try {
            dentistService.updateDentist(id, dto);
            ra.addFlashAttribute("success", "Cập nhật hồ sơ bác sĩ thành công!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/dentists";
    }

    @DeleteMapping("/api/delete/{id}")
    @ResponseBody
    public org.springframework.http.ResponseEntity<?> deleteDentist(@PathVariable("id") Long id) {
        try {
            dentistService.deleteDentist(id);
            return org.springframework.http.ResponseEntity.ok()
                    .body(java.util.Map.of("success", true, "message", "Xóa nha sĩ thành công!"));
        } catch (Exception e) {
            return org.springframework.http.ResponseEntity.badRequest()
                    .body(java.util.Map.of("success", false, "message", e.getMessage()));
        }
    }
}
