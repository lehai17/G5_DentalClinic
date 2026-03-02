package com.dentalclinic.controller.dentist;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;

@Controller
@RequestMapping("/dentist/busy-schedule")
public class DentistBusyController {

    @GetMapping
    public String showBusyForm() {
        // Trả về file HTML trong folder templates/Dentist/
        return "Dentist/busy-schedule";
    }

    @PostMapping("/submit")
    public String handleBusySchedule(
            @RequestParam("startDate") String startDate,
            @RequestParam("endDate") String endDate,
            @RequestParam("reason") String reason,
            RedirectAttributes redirectAttributes) {

        // Logic mẫu xử lý:
        // 1. Chuyển String sang LocalDate
        LocalDate start = LocalDate.parse(startDate);
        LocalDate end = LocalDate.parse(endDate);

        // 2. Gọi Service để kiểm tra lịch hẹn trùng (Logic bạn cần tự viết thêm ở Service)
        // appointmentService.notifyPatientsOfDentistLeave(dentistId, start, end, reason);

        // 3. Thông báo thành công và chuyển hướng về trang lịch làm việc
        redirectAttributes.addFlashAttribute("message", "Đã gửi báo cáo nghỉ thành công. Bệnh nhân sẽ được thông báo!");
        return "redirect:/dentist/work-schedule";
    }
}