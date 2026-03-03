package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.BusySchedule;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.service.admin.AdminBusyScheduleService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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

    // Inject đầy đủ các Bean cần thiết để xử lý logic và truy vấn dữ liệu
    public DentistBusyController(AdminBusyScheduleService adminBusyScheduleService,
                                 DentistProfileRepository dentistProfileRepository,
                                 DentistBusyScheduleRepository dentistBusyScheduleRepository) {
        this.adminBusyScheduleService = adminBusyScheduleService;
        this.dentistProfileRepository = dentistProfileRepository;
        this.dentistBusyScheduleRepository = dentistBusyScheduleRepository;
    }

    @GetMapping
    public String showBusyForm(Model model, Principal principal) {
        try {
            // 1. Lấy email của bác sĩ đang đăng nhập từ hệ thống
            String email = principal.getName();

            // 2. Tìm hồ sơ bác sĩ. Dùng Optional để tránh lỗi "Incompatible types"
            DentistProfile dentist = (DentistProfile) dentistProfileRepository.findByUserEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bác sĩ"));

            // 3. Lấy danh sách các yêu cầu nghỉ phép của riêng bác sĩ này để hiển thị ở bảng "My Absence History"
            List<BusySchedule> myRequests = dentistBusyScheduleRepository.findByDentistIdOrderByCreatedAtDesc(dentist.getId());

            model.addAttribute("myRequests", myRequests);
            return "Dentist/busy-schedule";

        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "Dentist/busy-schedule";
        }
    }

    @PostMapping("/submit")
    public String handleBusySchedule(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("reason") String reason,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            // 1. Chuyển đổi dữ liệu ngày tháng
            LocalDate start = LocalDate.parse(startDate);
            LocalDate end = LocalDate.parse(endDate);

            // 2. Lấy ID bác sĩ từ Principal để định danh người gửi đơn
            String email = principal.getName();
            DentistProfile dentist = (DentistProfile) dentistProfileRepository.findByUserEmail(email)
                    .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bác sĩ"));

            Long dentistId = dentist.getId();

            // 3. Gọi service để kiểm tra hạn mức (tối đa 2 buổi/tháng) và lưu đơn
            adminBusyScheduleService.submitBusyRequest(dentistId, start, end, reason);

            redirectAttributes.addFlashAttribute("message", "Đã gửi báo cáo nghỉ thành công!");
            return "redirect:/dentist/busy-schedule";

        } catch (Exception e) {
            // Bắt các lỗi như: Hết hạn mức nghỉ, sai định dạng ngày, không tìm thấy bác sĩ...
            redirectAttributes.addFlashAttribute("error", "Lỗi: " + e.getMessage());
            return "redirect:/dentist/busy-schedule";
        }
    }
}