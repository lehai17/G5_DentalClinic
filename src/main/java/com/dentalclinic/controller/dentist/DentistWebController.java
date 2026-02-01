package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.service.dentist.DentistSessionService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Controller
public class DentistWebController {

    private final AppointmentRepository appointmentRepository;
    private final DentistSessionService dentistSessionService;

    public DentistWebController(
            AppointmentRepository appointmentRepository,
            DentistSessionService dentistSessionService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.dentistSessionService = dentistSessionService;
    }

    @GetMapping("/dentist/work-schedule")
    public String workSchedule(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart,
            Model model
    ) {

        // 1. Dentist từ session
        Long dentistUserId = dentistSessionService.getCurrentDentistUserId();

        // 2. Snap tuần (Thứ 2 → Chủ nhật)
        LocalDate base = (weekStart != null) ? weekStart : LocalDate.now();
        LocalDate start = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);

        // 3. Danh sách ngày trong tuần
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(start.plusDays(i));
        }

        // 4. Khung giờ 08:00 → 17:00
        List<LocalTime> timeSlots = new ArrayList<>();
        LocalTime t = LocalTime.of(8, 0);
        while (t.isBefore(LocalTime.of(18, 0))) {
            timeSlots.add(t);
            t = t.plusHours(1);
        }

        // 5. Dropdown chọn tuần
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<Map<String, String>> weekOptions = new ArrayList<>();
        for (int i = -8; i <= 8; i++) {
            LocalDate ws = start.plusWeeks(i);
            Map<String, String> opt = new HashMap<>();
            opt.put("value", ws.toString());
            opt.put("label", ws.format(fmt) + " - " + ws.plusDays(6).format(fmt));
            weekOptions.add(opt);
        }

        // 6. Load appointments
        Map<String, ScheduleEventResponse> eventMap = new HashMap<>();
        if (dentistUserId != null) {
            List<Appointment> appts =
                    appointmentRepository.findScheduleForWeek(
                            dentistUserId, start, end
                    );
            eventMap = buildEventMap(appts);
        }

        // 7. Model
        model.addAttribute("dentistUserId", dentistUserId);
        model.addAttribute("weekStart", start);
        model.addAttribute("weekEnd", end);
        model.addAttribute("weekOptions", weekOptions);
        model.addAttribute("selectedWeekStart", start.toString());
        model.addAttribute("days", days);
        model.addAttribute("timeSlots", timeSlots);
        model.addAttribute("eventMap", eventMap);

        return "Dentist/work-schedule";
    }

    // Helper build event
    private Map<String, ScheduleEventResponse> buildEventMap(
            List<Appointment> appts
    ) {
        Map<String, ScheduleEventResponse> map = new HashMap<>();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        for (Appointment a : appts) {
            String key = a.getDate() + "_" + a.getStartTime().format(timeFmt);

            map.put(key, new ScheduleEventResponse(
                    a.getId(),
                    a.getCustomer().getUser().getId(),
                    a.getCustomer().getFullName(),
                    a.getService().getName(),
                    a.getDate(),
                    a.getStartTime(),
                    a.getEndTime(),
                    a.getStatus().name()
            ));
        }
        return map;
    }
}
