package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Controller
public class DentistWebController {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final DentistProfileRepository dentistProfileRepository;

    public DentistWebController(
            AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            DentistProfileRepository dentistProfileRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.dentistProfileRepository = dentistProfileRepository;
    }

    @GetMapping("/dentist/work-schedule")
    public String workSchedule(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate weekStart,
            Model model
    ) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long dentistUserId = user.getId();

        Long dentistProfileId = dentistProfileRepository
                .findByUser_Id(dentistUserId)
                .orElseThrow(() -> new RuntimeException("Dentist profile not found"))
                .getId();

        LocalDate base = (weekStart != null)
                ? weekStart
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        LocalDate start = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);

        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(start.plusDays(i));
        }

        List<LocalTime> timeSlots = new ArrayList<>();

        for (int h = 8; h < 17; h++) {
            timeSlots.add(LocalTime.of(h, 0));
            timeSlots.add(LocalTime.of(h, 30));
        }

        timeSlots.add(LocalTime.of(17, 0));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<Map<String, String>> weekOptions = new ArrayList<>();

        for (int i = -6; i <= 6; i++) {
            LocalDate ws = start.plusWeeks(i);
            Map<String, String> opt = new HashMap<>();
            opt.put("value", ws.toString());
            opt.put("label", ws.format(fmt) + " - " + ws.plusDays(6).format(fmt));
            weekOptions.add(opt);
        }

        List<Appointment> appts =
                appointmentRepository.findScheduleForWeek(
                        dentistProfileId,
                        start,
                        end
                );

        Map<String, ScheduleEventResponse> eventMap = new HashMap<>();
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

        for (Appointment a : appts) {

            String key = a.getDate() + "_" + a.getStartTime().format(tf);

            long duration = Duration.between(
                    a.getStartTime(),
                    a.getEndTime()
            ).toMinutes();

            int span = (int) Math.ceil(duration / 30.0);

            eventMap.put(key, new ScheduleEventResponse(
                    a.getId(),
                    a.getCustomer().getUser().getId(),
                    a.getCustomer().getFullName(),
                    a.getService().getName(),
                    a.getDate(),
                    a.getStartTime(),
                    a.getEndTime(),
                    a.getStatus().name(),
                    span
            ));
        }

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

    @GetMapping("/dentist/dashboard")
    public String dashboard(Model model) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Long dentistUserId = user.getId();

        Long dentistProfileId = dentistProfileRepository
                .findByUser_Id(dentistUserId)
                .orElseThrow(() -> new RuntimeException("Dentist profile not found"))
                .getId();

        LocalDate today = LocalDate.now();

        long total = appointmentRepository
                .countTotalByDentistAndDate(dentistProfileId, today);

        long completed = appointmentRepository
                .countCompletedByDentistAndDate(dentistProfileId, today);

        long remaining = total - completed;

        int completionRate = 0;
        if (total > 0) {
            completionRate = (int) ((completed * 100.0) / total);
        }

        model.addAttribute("totalToday", total);
        model.addAttribute("completedToday", completed);
        model.addAttribute("remainingToday", remaining);
        model.addAttribute("completionRate", completionRate);

        return "Dentist/dashboard";
    }
}