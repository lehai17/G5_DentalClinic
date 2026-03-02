package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.repository.AppointmentRepository;
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

    public DentistWebController(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @GetMapping("/dentist/work-schedule")
    public String workSchedule(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart,
            Model model
    ) {

        // ✅ FIX CỨNG để test
        Long dentistUserId = 1L;

        // ===== SNAP TUẦN
        LocalDate base = (weekStart != null) ? weekStart : LocalDate.now();
        LocalDate start = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);

        // ===== DAYS
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(start.plusDays(i));
        }

        // ===== TIME SLOTS
        List<LocalTime> timeSlots = new ArrayList<>();
        for (int h = 8; h <= 17; h++) {
            timeSlots.add(LocalTime.of(h, 0));
        }

        // ===== WEEK DROPDOWN
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<Map<String, String>> weekOptions = new ArrayList<>();
        for (int i = -6; i <= 6; i++) {
            LocalDate ws = start.plusWeeks(i);
            Map<String, String> opt = new HashMap<>();
            opt.put("value", ws.toString());
            opt.put("label", ws.format(fmt) + " - " + ws.plusDays(6).format(fmt));
            weekOptions.add(opt);
        }

        // ===== LOAD APPOINTMENTS
        List<Appointment> appts =
                appointmentRepository.findScheduleForWeek(dentistUserId, start, end);

        Map<String, ScheduleEventResponse> eventMap = new HashMap<>();
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

        for (Appointment a : appts) {
            String key = a.getDate() + "_" + a.getStartTime().format(tf);

            if (eventMap.containsKey(key)) {
                ScheduleEventResponse existing = eventMap.get(key);

                // 🔒 COMPLETED luôn thắng
                if ("COMPLETED".equals(existing.getStatus())) {
                    continue;
                }
            }

            eventMap.put(key, new ScheduleEventResponse(
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


        // ===== MODEL
        model.addAttribute("dentistUserId", dentistUserId);
        model.addAttribute("dentistName", "Dentist");
        model.addAttribute("weekStart", start);
        model.addAttribute("weekEnd", end);
        model.addAttribute("weekOptions", weekOptions);
        model.addAttribute("selectedWeekStart", start.toString());
        model.addAttribute("days", days);
        model.addAttribute("timeSlots", timeSlots);
        model.addAttribute("eventMap", eventMap);

        return "Dentist/work-schedule";
    }
}
