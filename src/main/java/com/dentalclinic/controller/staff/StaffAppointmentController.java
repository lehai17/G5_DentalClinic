package com.dentalclinic.controller.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.service.staff.StaffAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.dentalclinic.service.mail.EmailService;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;


@Controller
@RequestMapping("/staff")
public class StaffAppointmentController {

    @Autowired
    private StaffAppointmentService staffAppointmentService;

    @Autowired
    private EmailService emailService;


    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false, defaultValue = "today") String view,
                            Model model) {

        var appointments = staffAppointmentService.getAllAppointments();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today;
        LocalDate endDate = today;

        // Xác định khoảng thời gian
        switch (view) {
            case "week" -> {
                startDate = today.with(DayOfWeek.MONDAY);
                endDate = today.with(DayOfWeek.SUNDAY);
            }
            case "month" -> {
                startDate = today.withDayOfMonth(1);
                endDate = today.withDayOfMonth(today.lengthOfMonth());
            }
            default -> {
                // today
                startDate = today;
                endDate = today;
            }
        }

        final LocalDate fromDate = startDate;
        final LocalDate toDate = endDate;

        var filtered = appointments.stream()
                .filter(a ->
                        !a.getDate().isBefore(fromDate)
                                && !a.getDate().isAfter(toDate)
                )
                .toList();

        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("staffName", "Staff");

        model.addAttribute("view", view);

        model.addAttribute("totalCount", filtered.size());
        model.addAttribute(
                "pendingCount",
                filtered.stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
                        .count()
        );
        model.addAttribute(
                "completedCount",
                filtered.stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                        .count()
        );
        model.addAttribute(
                "cancelledCount",
                filtered.stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED)
                        .count()
        );

        return "staff/dashboard";
    }

    @GetMapping("/appointments")
    public String appointments(@RequestParam(required = false) String keyword,
                               @RequestParam(required = false) String sort,
                               Model model) {

        model.addAttribute("pageTitle", "Quản lý lịch khám");
        model.addAttribute("staffName", "Staff");

        List<Appointment> appointments =
                staffAppointmentService.searchAndSort(keyword, sort);
        model.addAttribute("keyword", keyword);
        model.addAttribute("sort", sort);
        model.addAttribute("appointments", appointments);

        return "staff/appointments";
    }

    @PostMapping("/appointments/confirm")
    @ResponseBody
    public void confirm(@RequestParam Long id) {
        staffAppointmentService.confirmAppointment(id);
    }

    @PostMapping("/appointments/assign")
    @ResponseBody
    public void assign(
            @RequestParam Long appointmentId,
            @RequestParam Long dentistId) {
        staffAppointmentService.assignDentist(appointmentId, dentistId);
    }

    @PostMapping("/appointments/complete")
    @ResponseBody
    public void complete(@RequestParam Long id) {
        staffAppointmentService.completeAppointment(id);
    }


    @PostMapping("/appointments/cancel")
    @ResponseBody
    public void cancel(
            @RequestParam Long id,
            @RequestParam String reason) {
        staffAppointmentService.cancelAppointment(id, reason);
    }



}
