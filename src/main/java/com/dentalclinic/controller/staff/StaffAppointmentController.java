package com.dentalclinic.controller.staff;

import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.service.staff.StaffAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/staff")
public class StaffAppointmentController {

    @Autowired
    private StaffAppointmentService staffAppointmentService;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {

        var appointments = staffAppointmentService.getAllAppointments();

        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("staffName", "Staff");

        model.addAttribute("todayCount", appointments.size());
        model.addAttribute(
                "pendingCount",
                appointments.stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
                        .count()
        );

        return "staff/dashboard";
    }

    @GetMapping("/appointments")
    public String appointments(Model model) {

        model.addAttribute("pageTitle", "Quản lý lịch khám");
        model.addAttribute("staffName", "Staff");
        model.addAttribute(
                "appointments",
                staffAppointmentService.getAllAppointments()
        );

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
