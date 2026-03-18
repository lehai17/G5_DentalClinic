package com.dentalclinic.controller.staff;

import com.dentalclinic.service.staff.StaffAppointmentResultService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/staff/appointments")
public class StaffAppointmentResultController {

    private final StaffAppointmentResultService staffAppointmentResultService;

    public StaffAppointmentResultController(StaffAppointmentResultService staffAppointmentResultService) {
        this.staffAppointmentResultService = staffAppointmentResultService;
    }

    @GetMapping("/{id}/result")
    public String viewResult(@PathVariable Long id, Model model) {
        var result = staffAppointmentResultService.load(id);

        var appointment = result.appointment();
        var medicalRecord = result.medicalRecord();

        model.addAttribute("pageTitle", "Appointment Result");
        model.addAttribute("appointment", appointment);
        model.addAttribute("medicalRecord", medicalRecord);

        model.addAttribute("customer", appointment.getCustomer());
        model.addAttribute("dentist", appointment.getDentist());
        model.addAttribute("service", appointment.getService());

        return "staff/appointment-result";
    }
}