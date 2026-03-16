package com.dentalclinic.controller.admin;

import com.dentalclinic.dto.admin.AdminAgendaItemDto;
import com.dentalclinic.dto.admin.SlotDayBadgeDto;
import com.dentalclinic.service.admin.AdminSlotService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin/slots")
public class AdminSlotController {
    private static final DateTimeFormatter YM_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final AdminSlotService adminSlotService;

    public AdminSlotController(AdminSlotService adminSlotService) {
        this.adminSlotService = adminSlotService;
    }

    @GetMapping
    public String slotDashboard(@RequestParam(required = false) String month,
            @RequestParam(required = false) String date,
            @RequestParam(required = false, defaultValue = "dentists") String mode,
            @RequestParam(required = false) Long dentistId,
            Model model) {
        return renderSlotPage(month, date, mode, dentistId, model);
    }

    @GetMapping("/{date:[0-9]{4}-[0-9]{2}-[0-9]{2}}")
    public String slotDashboardByDate(@PathVariable String date,
            @RequestParam(required = false) String month,
            @RequestParam(required = false, defaultValue = "dentists") String mode,
            @RequestParam(required = false) Long dentistId,
            Model model) {
        return renderSlotPage(month, date, mode, dentistId, model);
    }

    private String renderSlotPage(String month, String date, String mode, Long dentistId, Model model) {
        try {
            boolean dateSelected = (date != null && !date.isBlank());
            model.addAttribute("dateSelected", dateSelected);

            YearMonth ym;
            try {
                ym = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month, YM_FMT);
            } catch (Exception e) {
                ym = YearMonth.now();
            }

            LocalDate selectedDate;
            try {
                selectedDate = (date == null || date.isBlank()) ? LocalDate.now() : LocalDate.parse(date);
            } catch (Exception e) {
                selectedDate = LocalDate.now();
            }

            model.addAttribute("month", ym.toString());
            model.addAttribute("prevMonth", ym.minusMonths(1).toString());
            model.addAttribute("nextMonth", ym.plusMonths(1).toString());
            model.addAttribute("monthName", ym.format(DateTimeFormatter.ofPattern("MMMM yyyy")));

            model.addAttribute("selectedDate", selectedDate.toString());
            model.addAttribute("defaultToDate", selectedDate.plusDays(7).toString());
            model.addAttribute("selectedDateName",
                    selectedDate.format(DateTimeFormatter.ofPattern("EEEE, dd MMMM, yyyy")));

            model.addAttribute("activePage", "slots");
            model.addAttribute("mode", mode != null ? mode : "dentists");

            // Add dentist list for filtering
            model.addAttribute("dentists", adminSlotService.getAllActiveDentistsForDate(selectedDate));

            return "admin/slots";
        } catch (Exception e) {
            model.addAttribute("error", "Failed to render page: " + e.getMessage());
            return "admin/slots";
        }
    }

    @GetMapping("/api/agenda")
    @ResponseBody
    public List<AdminAgendaItemDto> apiAgenda(@RequestParam String date,
            @RequestParam(required = false) Long dentistId) {
        return adminSlotService.getAgenda(LocalDate.parse(date), dentistId);
    }

    @GetMapping("/api/appointment/{id}")
    @ResponseBody
    public AdminAgendaItemDto getAppointmentDetail(@PathVariable Long id) {
        return adminSlotService.getAppointmentDetail(id);
    }

    @GetMapping("/api/calendar")
    @ResponseBody
    public Map<String, SlotDayBadgeDto> apiCalendar(@RequestParam String month) {
        YearMonth ym = YearMonth.parse(month, YM_FMT);
        LocalDate gridStart = ym.atDay(1);
        while (gridStart.getDayOfWeek() != DayOfWeek.MONDAY) {
            gridStart = gridStart.minusDays(1);
        }
        LocalDate gridEnd = gridStart.plusDays(41);
        List<SlotDayBadgeDto> badges = adminSlotService.getBadgesInRange(gridStart, gridEnd);
        Map<String, SlotDayBadgeDto> badgeMap = new HashMap<>();
        if (badges != null) {
            for (SlotDayBadgeDto b : badges) {
                if (b != null && b.getDate() != null) {
                    badgeMap.put(b.getDate().toString(), b);
                }
            }
        }
        return badgeMap;
    }

    @PostMapping("/lock-day")
    public String lockDay(@RequestParam String date, @RequestParam(required = false) String reason,
            RedirectAttributes ra) {
        try {
            adminSlotService.lockDay(LocalDate.parse(date), reason);
            ra.addFlashAttribute("message", "Đã khóa toàn bộ các ca khám ngày " + date);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/admin/slots?date=" + date;
    }

    @PostMapping("/unlock-day")
    public String unlockDay(@RequestParam String date, RedirectAttributes ra) {
        adminSlotService.unlockDay(LocalDate.parse(date));
        ra.addFlashAttribute("message", "Đã mở khóa toàn bộ các ca khám ngày " + date);
        return "redirect:/admin/slots?date=" + date;
    }

    @PostMapping("/generate")
    public String generateSlots(@RequestParam String fromDate,
            @RequestParam String toDate,
            @RequestParam int capacity,
            RedirectAttributes ra) {
        int count = adminSlotService.generateSlots(LocalDate.parse(fromDate), LocalDate.parse(toDate), capacity);
        ra.addFlashAttribute("message", "Đã tạo/khôi phục " + count + " slots.");
        return "redirect:/admin/slots?date=" + fromDate;
    }

    @PostMapping("/api/lock-day")
    @ResponseBody
    public ResponseEntity<Map<String, String>> apiLockDay(@RequestParam String date,
            @RequestParam(required = false) String reason) {
        try {
            adminSlotService.lockDay(LocalDate.parse(date), reason);
            return ResponseEntity.ok(Map.of("message", "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/unlock-day")
    @ResponseBody
    public ResponseEntity<Map<String, String>> apiUnlockDay(@RequestParam String date) {
        try {
            adminSlotService.unlockDay(LocalDate.parse(date));
            return ResponseEntity.ok(Map.of("message", "Success"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/reset-all")
    public String resetAllSlots(RedirectAttributes ra) {
        adminSlotService.resetAllSlots();
        ra.addFlashAttribute("message", "Đã reset tất cả các ngày về trạng thái MỞ.");
        return "redirect:/admin/slots";
    }
}
