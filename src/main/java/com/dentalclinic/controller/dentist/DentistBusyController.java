package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.BusySchedule;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.service.admin.AdminBusyScheduleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/dentist/busy-schedule")
public class DentistBusyController {

    private final AdminBusyScheduleService adminBusyScheduleService;
    private final DentistProfileRepository dentistProfileRepository;
    private final DentistBusyScheduleRepository dentistBusyScheduleRepository;

    public DentistBusyController(AdminBusyScheduleService adminBusyScheduleService,
                                 DentistProfileRepository dentistProfileRepository,
                                 DentistBusyScheduleRepository dentistBusyScheduleRepository) {
        this.adminBusyScheduleService = adminBusyScheduleService;
        this.dentistProfileRepository = dentistProfileRepository;
        this.dentistBusyScheduleRepository = dentistBusyScheduleRepository;
    }

    @GetMapping
    public String showBusyForm(Model model,
                               Principal principal,
                               @RequestParam(required = false) Long editId) {
        try {
            DentistProfile dentist = getCurrentDentist(principal);
            List<BusySchedule> myRequests = dentistBusyScheduleRepository.findByDentistIdOrderByCreatedAtDesc(dentist.getId());

            model.addAttribute("myRequests", myRequests);
            if (editId != null) {
                BusySchedule editingRequest = dentistBusyScheduleRepository.findByIdAndDentistId(editId, dentist.getId())
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn xin nghỉ."));
                model.addAttribute("editingRequest", editingRequest);
            }
            return "Dentist/busy-schedule";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "Dentist/busy-schedule";
        }
    }

    @PostMapping("/submit")
    public String handleBusySchedule(@RequestParam("startDate") String startDate,
                                     @RequestParam("endDate") String endDate,
                                     @RequestParam("reason") String reason,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            if (startDate == null || startDate.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ngày bắt đầu.");
                return "redirect:/dentist/busy-schedule";
            }
            if (endDate == null || endDate.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ngày kết thúc.");
                return "redirect:/dentist/busy-schedule";
            }
            if (reason == null || reason.trim().isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do xin nghỉ.");
                return "redirect:/dentist/busy-schedule";
            }

            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            LocalDate today = LocalDate.now();

            if (start.isBefore(today)) {
                redirectAttributes.addFlashAttribute("error", "Không thể đăng ký nghỉ cho ngày đã qua hoặc ngày hôm nay.");
                return "redirect:/dentist/busy-schedule";
            }
            if (end.isBefore(start)) {
                redirectAttributes.addFlashAttribute("error", "Ngày kết thúc không được trước ngày bắt đầu.");
                return "redirect:/dentist/busy-schedule";
            }

            DentistProfile dentist = getCurrentDentist(principal);
            adminBusyScheduleService.submitBusyRequest(dentist.getId(), start, end, reason);

            redirectAttributes.addFlashAttribute("message", "Gửi đơn xin nghỉ thành công.");
            return "redirect:/dentist/busy-schedule";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/dentist/busy-schedule";
        }
    }

    @PostMapping("/update/{id}")
    public String updateBusySchedule(@PathVariable Long id,
                                     @RequestParam("startDate") String startDate,
                                     @RequestParam("endDate") String endDate,
                                     @RequestParam("reason") String reason,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            if (startDate == null || startDate.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ngày bắt đầu.");
                return "redirect:/dentist/busy-schedule?editId=" + id;
            }
            if (endDate == null || endDate.isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng chọn ngày kết thúc.");
                return "redirect:/dentist/busy-schedule?editId=" + id;
            }
            if (reason == null || reason.trim().isBlank()) {
                redirectAttributes.addFlashAttribute("error", "Vui lòng nhập lý do xin nghỉ.");
                return "redirect:/dentist/busy-schedule?editId=" + id;
            }

            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);
            LocalDate today = LocalDate.now();

            if (start.isBefore(today)) {
                redirectAttributes.addFlashAttribute("error", "Không thể đăng ký nghỉ cho ngày đã qua hoặc ngày hôm nay.");
                return "redirect:/dentist/busy-schedule?editId=" + id;
            }
            if (end.isBefore(start)) {
                redirectAttributes.addFlashAttribute("error", "Ngày kết thúc không được trước ngày bắt đầu.");
                return "redirect:/dentist/busy-schedule?editId=" + id;
            }

            DentistProfile dentist = getCurrentDentist(principal);
            adminBusyScheduleService.updateBusyRequest(id, dentist.getId(), start, end, reason);

            redirectAttributes.addFlashAttribute("message", "Cập nhật đơn xin nghỉ thành công.");
            return "redirect:/dentist/busy-schedule";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/dentist/busy-schedule?editId=" + id;
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteBusySchedule(@PathVariable Long id,
                                     Principal principal,
                                     RedirectAttributes redirectAttributes) {
        try {
            DentistProfile dentist = getCurrentDentist(principal);
            adminBusyScheduleService.deleteBusyRequest(id, dentist.getId());
            redirectAttributes.addFlashAttribute("message", "Xóa đơn xin nghỉ thành công.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/dentist/busy-schedule";
    }

    private DentistProfile getCurrentDentist(Principal principal) {
        String email = principal.getName();
        return (DentistProfile) dentistProfileRepository.findByUserEmail(email)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bác sĩ"));
    }
}
