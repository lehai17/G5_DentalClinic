package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.DentistDTO;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.service.dentist.DentistService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/admin/dentists") // Sửa lại để quản lý tập trung chuyên mục Dentist
public class AdminUserController {

    @Autowired
    private DentistProfileRepository dentistProfileRepository;

    @Autowired
    private DentistService dentistService; // BỔ SUNG: Khai báo Service để gọi hàm save

    // 1. Hiển thị danh sách: localhost:8080/admin/dentists
    @GetMapping
    public String showDentistList(Model model) {
        List<DentistProfile> dentists = dentistProfileRepository.findAll();
        model.addAttribute("dentists", dentists);
        model.addAttribute("totalDentists", dentists.size());
        return "admin/dentist-list";
    }

    // 2. Hiển thị Form thêm mới: localhost:8080/admin/dentists/add
    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("dentistDTO", new DentistDTO());
        return "admin/add-dentist";
    }

    // 3. Xử lý lưu dữ liệu: localhost:8080/admin/dentists/save
    @PostMapping("/save")
    public String processAddDentist(@ModelAttribute("dentistDTO") DentistDTO dto) {
        // SỬA LỖI: Sử dụng biến đối tượng dentistService (chữ d viết thường)
        dentistService.saveDentist(dto);
        return "redirect:/admin/dentists";
    }
}