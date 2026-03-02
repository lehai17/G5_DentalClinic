package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.admin.ServiceDTO;
import com.dentalclinic.service.admin.AdminServiceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/services")
public class AdminServiceController {

    @Autowired
    private AdminServiceService adminServiceService;

    @GetMapping
    public String viewServiceList(Model model) {
        model.addAttribute("services", adminServiceService.getAllServices());
        model.addAttribute("activePage", "services");
        return "admin/service-list";
    }

    @GetMapping("/api/get/{id}")
    @ResponseBody
    public ResponseEntity<ServiceDTO> getService(@PathVariable("id") Long id) {
        try {
            ServiceDTO service = adminServiceService.getServiceById(id);
            return ResponseEntity.ok(service);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/add")
    public String addService(@ModelAttribute ServiceDTO serviceDTO,
            @RequestParam(value = "image", required = false) MultipartFile image,
            RedirectAttributes redirectAttributes) {
        try {
            adminServiceService.addService(serviceDTO, image);
            redirectAttributes.addFlashAttribute("success", "Thêm dịch vụ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/services";
    }

    @PostMapping("/update/{id}")
    public String updateService(@PathVariable("id") Long id,
            @ModelAttribute ServiceDTO serviceDTO,
            @RequestParam(value = "image", required = false) MultipartFile image,
            RedirectAttributes redirectAttributes) {
        try {
            adminServiceService.updateService(id, serviceDTO, image);
            redirectAttributes.addFlashAttribute("success", "Cập nhật dịch vụ thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/services";
    }

    @PostMapping("/toggle/{id}")
    public String toggleServiceStatus(@PathVariable("id") Long id, @RequestParam("status") boolean status,
            RedirectAttributes redirectAttributes) {
        try {
            adminServiceService.toggleServiceStatus(id, status);
            redirectAttributes.addFlashAttribute("success", "Cập nhật trạng thái thành công!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
        }
        return "redirect:/admin/services";
    }
}
