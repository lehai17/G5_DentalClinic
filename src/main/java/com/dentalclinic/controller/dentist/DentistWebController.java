package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.DentistProfileRepository;
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
    private final DentistProfileRepository dentistProfileRepository;
    private final DentistSessionService dentistSessionService;

    public DentistWebController(AppointmentRepository appointmentRepository,
                                DentistProfileRepository dentistProfileRepository,
                                DentistSessionService dentistSessionService) {
        this.appointmentRepository = appointmentRepository;
        this.dentistProfileRepository = dentistProfileRepository;
        this.dentistSessionService = dentistSessionService;
    }

    @GetMapping("/dentist/work-schedule")
    public String workSchedule(
            @RequestParam(required = false) Long dentistUserId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            Model model
    ) {

        // ========= 1. Resolve dentist user =========
        Long userId = resolveDentistUserId(dentistUserId);

        // ========= 2. Calculate week =========
        LocalDate base = (weekStart != null) ? weekStart : LocalDate.now();
        LocalDate start = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate end = start.plusDays(6);

        // ========= 3. Days =========
        List<LocalDate> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            days.add(start.plusDays(i));
        }

        // ========= 4. Time slots =========
        List<LocalTime> timeSlots = new ArrayList<>();
        LocalTime t = LocalTime.of(8, 0);
        while (!t.isAfter(LocalTime.of(18, 0))) {
            timeSlots.add(t);
            t = t.plusHours(1);
        }

        // ========= 5. Week dropdown =========
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");
        List<Map<String, String>> weekOptions = new ArrayList<>();
        for (int i = -8; i <= 8; i++) {
            LocalDate ws = start.plusWeeks(i);
            Map<String, String> opt = new HashMap<>();
            opt.put("value", ws.toString());
            opt.put("label", ws.format(fmt) + " To " + ws.plusDays(6).format(fmt));
            weekOptions.add(opt);
        }

        // ========= 6. Base model (LUÔN SET – DB RỖNG VẪN CHẠY) =========
        model.addAttribute("dentistUserId", userId);
        model.addAttribute("weekStart", start);
        model.addAttribute("weekEnd", end);
        model.addAttribute("weekOptions", weekOptions);
        model.addAttribute("selectedWeekStart", start.toString());
        model.addAttribute("days", days);
        model.addAttribute("timeSlots", timeSlots);

        // ========= 7. Dentist profile =========
        DentistProfile dentistProfile =
                dentistProfileRepository.findByUser_Id(userId).orElse(null);

        if (dentistProfile == null) {
            // 👉 DB TRỐNG / CHƯA INSERT → KHÔNG 500
            model.addAttribute("dentistName", "Dentist");
            model.addAttribute("eventMap", Collections.emptyMap());
            model.addAttribute("showAppointments", false);
            return "Dentist/work-schedule";
        }

        // ========= 8. Load appointments =========
        List<Appointment> appts =
                appointmentRepository.findScheduleForWeek(
                        dentistProfile.getId(), start, end
                );

        Map<String, ScheduleEventResponse> eventMap =
                buildEventMap(appts, userId);

        model.addAttribute("dentistName", dentistProfile.getFullName());
        model.addAttribute("eventMap", eventMap);
        model.addAttribute("showAppointments", true);

        return "Dentist/work-schedule";
    }

    private Long resolveDentistUserId(Long dentistUserId) {
        if (dentistUserId != null) return dentistUserId;

        Long current = dentistSessionService.getCurrentDentistUserId();
        if (current == null) {
            throw new IllegalArgumentException("Missing dentistUserId");
        }
        return current;
    }

    private Map<String, ScheduleEventResponse> buildEventMap(
            List<Appointment> appts,
            Long dentistUserId
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
                    a.getStatus().name(),
                    "/dentist/appointments/" + a.getId() + "/examination"
                            + "?customerUserId=" + a.getCustomer().getUser().getId()
                            + "&dentistUserId=" + dentistUserId,
                    "/dentist/appointments/" + a.getId() + "/billing-transfer"
                            + "?customerUserId=" + a.getCustomer().getUser().getId()
                            + "&dentistUserId=" + dentistUserId
            ));
        }
        return map;
    }
}
