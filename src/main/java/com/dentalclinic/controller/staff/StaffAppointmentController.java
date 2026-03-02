package com.dentalclinic.controller.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.service.staff.StaffAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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
                               @RequestParam(defaultValue = "") String serviceKeyword,
                               @RequestParam(required = false) String sort,
                               @RequestParam(defaultValue = "0") int page,
                               Model model) {

        model.addAttribute("pageTitle", "Appointment Management");
        model.addAttribute("staffName", "Staff");

        Page<Appointment> appointmentPage =
                staffAppointmentService.searchAndSort(keyword, serviceKeyword, sort, page);

        model.addAttribute("appointments", appointmentPage.getContent());


        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", appointmentPage.getTotalPages());

        model.addAttribute("keyword", keyword);
        model.addAttribute("serviceKeyword", serviceKeyword);
        model.addAttribute("sort", sort);

        return "staff/appointments";
    }

    @PostMapping("/appointments/confirm")
    @ResponseBody
    public void confirm(@RequestParam Long id) {
        staffAppointmentService.confirmAppointment(id);
    }

    @PostMapping("/appointments/assign")
    @ResponseBody
    public ResponseEntity<?> assign(
            @RequestParam Long appointmentId,
            @RequestParam Long dentistId) {

        try {
            staffAppointmentService.assignDentist(appointmentId, dentistId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
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
    @PostMapping("/appointments/{id}/check-in")
    @ResponseBody
    public void checkIn(@PathVariable Long id) {
        staffAppointmentService.checkInAppointment(id);
    }


}
