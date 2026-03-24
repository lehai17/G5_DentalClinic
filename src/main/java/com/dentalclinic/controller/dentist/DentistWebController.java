package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
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
import java.util.stream.Collectors;

@Controller
public class DentistWebController {
    private static final Logger log = LoggerFactory.getLogger(DentistWebController.class);

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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

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
            if (a == null) {
                continue;
            }
            if (a.getDate() == null || a.getStartTime() == null || a.getEndTime() == null) {
                log.warn("Skip invalid appointment in work schedule: missing date/time. appointmentId={}", a.getId());
                continue;
            }
            if (!a.getEndTime().isAfter(a.getStartTime())) {
                log.warn("Skip invalid appointment in work schedule: endTime must be after startTime. appointmentId={}", a.getId());
                continue;
            }

            String key = a.getDate() + "_" + a.getStartTime().format(tf);

            long duration = Duration.between(
                    a.getStartTime(),
                    a.getEndTime()
            ).toMinutes();

            int span = Math.max(1, (int) Math.ceil(duration / 30.0));
            Long customerUserId = (a.getCustomer() != null && a.getCustomer().getUser() != null)
                    ? a.getCustomer().getUser().getId()
                    : null;
            String patientName = (a.getCustomer() != null && a.getCustomer().getFullName() != null)
                    ? a.getCustomer().getFullName()
                    : "(Khach hang khong ro)";
            String status = a.getStatus() != null ? a.getStatus().name() : AppointmentStatus.CONFIRMED.name();

            eventMap.put(key, new ScheduleEventResponse(
                    a.getId(),
                    customerUserId,
                    patientName,
                    buildServiceLabel(a),
                    a.getDate(),
                    a.getStartTime(),
                    a.getEndTime(),
                    status,
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        Long dentistUserId = user.getId();

        Long dentistProfileId = dentistProfileRepository
                .findByUser_Id(dentistUserId)
                .orElseThrow(() -> new RuntimeException("Dentist profile not found"))
                .getId();

        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();

        long total = appointmentRepository
                .countTotalByDentistAndDate(dentistProfileId, today);

        long completed = appointmentRepository
                .countCompletedByDentistAndDate(dentistProfileId, today);

        long remaining = total - completed;

        int completionRate = 0;
        if (total > 0) {
            completionRate = (int) ((completed * 100.0) / total);
        }

        // Weekly stats (Mon -> Sun)
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);

        Map<LocalDate, Map<AppointmentStatus, Long>> countsByDay = new HashMap<>();
        for (Object[] row : appointmentRepository.countStatusByDentistAndDateRange(dentistProfileId, weekStart, weekEnd)) {
            if (row == null || row.length < 3) continue;
            LocalDate date = (LocalDate) row[0];
            AppointmentStatus status = (AppointmentStatus) row[1];
            Long count = (Long) row[2];
            if (date == null || status == null || count == null) continue;
            countsByDay.computeIfAbsent(date, k -> new EnumMap<>(AppointmentStatus.class))
                    .merge(status, count, Long::sum);
        }

        List<LocalDate> weekDays = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            weekDays.add(weekStart.plusDays(i));
        }

        long totalWeek = 0;
        long completedWeek = 0;
        long pendingWeek = 0;
        long examiningWeek = 0;

        List<DailyStatView> dailyStats = new ArrayList<>();
        for (LocalDate d : weekDays) {
            Map<AppointmentStatus, Long> map = countsByDay.getOrDefault(d, Map.of());

            long finishedCount = map.getOrDefault(AppointmentStatus.COMPLETED, 0L)
                    + map.getOrDefault(AppointmentStatus.WAITING_PAYMENT, 0L);

            long examiningCount = map.getOrDefault(AppointmentStatus.EXAMINING, 0L);

            long pendingCount = map.getOrDefault(AppointmentStatus.CONFIRMED, 0L)
                    + map.getOrDefault(AppointmentStatus.CHECKED_IN, 0L);

            long dayTotal = finishedCount + examiningCount + pendingCount;

            totalWeek += dayTotal;
            completedWeek += finishedCount;
            examiningWeek += examiningCount;
            pendingWeek += pendingCount;

            dailyStats.add(new DailyStatView(
                    d,
                    d.format(DateTimeFormatter.ofPattern("dd/MM")),
                    finishedCount,
                    examiningCount,
                    pendingCount,
                    dayTotal
            ));
        }

        long maxDailyTotal = dailyStats.stream().mapToLong(DailyStatView::total).max().orElse(0L);
        long safeMaxDailyTotal = Math.max(1L, maxDailyTotal);

        DailyStatView busiest = dailyStats.stream()
                .max(Comparator.comparingLong(DailyStatView::total))
                .orElse(null);

        // Upcoming: closest 3
        List<AppointmentStatus> upcomingStatuses = List.of(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.CHECKED_IN,
                AppointmentStatus.EXAMINING
        );

        List<UpcomingAppointmentView> upcomingAppointments = appointmentRepository
                .findUpcomingForDentist(dentistProfileId, upcomingStatuses, today, PageRequest.of(0, 20))
                .stream()
                .filter(a -> a.getDate() != null)
                .filter(a -> a.getDate().isAfter(today)
                        || (a.getDate().isEqual(today)
                        && (
                        a.getStatus() == AppointmentStatus.EXAMINING
                                || (a.getStartTime() != null && !a.getStartTime().isBefore(now))
                                || (a.getEndTime() != null && !a.getEndTime().isBefore(now))
                )))
                .limit(3)
                .map(a -> new UpcomingAppointmentView(
                        a.getId(),
                        a.getCustomer() != null ? a.getCustomer().getFullName() : "",
                        a.getCustomer() != null ? a.getCustomer().getPhone() : "",
                        buildServiceLabel(a),
                        a.getDate(),
                        a.getStartTime(),
                        a.getEndTime(),
                        a.getStatus(),
                        (a.getDate() != null
                                ? a.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
                                : weekStart.toString())
                ))
                .toList();

        model.addAttribute("totalToday", total);
        model.addAttribute("completedToday", completed);
        model.addAttribute("remainingToday", remaining);
        model.addAttribute("completionRate", completionRate);

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("weekEnd", weekEnd);
        model.addAttribute("totalWeek", totalWeek);
        model.addAttribute("completedWeek", completedWeek);
        model.addAttribute("examiningWeek", examiningWeek);
        model.addAttribute("pendingWeek", pendingWeek);
        model.addAttribute("dailyStats", dailyStats);
        model.addAttribute("maxDailyTotal", safeMaxDailyTotal);
        model.addAttribute("busiestDayLabel", busiest != null && busiest.total() > 0 ? busiest.label() : null);
        model.addAttribute("busiestDayTotal", busiest != null ? busiest.total() : 0);
        model.addAttribute("upcomingAppointments", upcomingAppointments);

        return "Dentist/dashboard";
    }

    private String buildServiceLabel(Appointment appointment) {
        if (appointment == null) {
            return "";
        }

        List<AppointmentDetail> details = appointment.getAppointmentDetails();
        if (details != null && !details.isEmpty()) {
            String joined = details.stream()
                    .sorted(Comparator.comparing(
                            AppointmentDetail::getDetailOrder,
                            Comparator.nullsLast(Integer::compareTo)
                    ))
                    .map(detail -> {
                        String name = detail.getServiceNameSnapshot();
                        if (name == null || name.isBlank()) {
                            if (detail.getService() != null) {
                                name = detail.getService().getName();
                            }
                        }
                        return name;
                    })
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));

            if (!joined.isBlank()) {
                return joined;
            }
        }

        if (appointment.getService() != null && appointment.getService().getName() != null) {
            return appointment.getService().getName();
        }

        return "";
    }

    private record DailyStatView(
            LocalDate date,
            String label,
            long completed,
            long examining,
            long pending,
            long total
    ) {}

    private record UpcomingAppointmentView(
            Long id,
            String patientName,
            String phone,
            String serviceLabel,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            AppointmentStatus status,
            String weekStart
    ) {}
}
