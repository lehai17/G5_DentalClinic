package com.dentalclinic.controller.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.service.staff.StaffScheduleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/staff/schedules")
public class StaffScheduleController {

    @Autowired
    private StaffScheduleService staffScheduleService;

    /* URL: /staff/schedules */
    @GetMapping
    public String viewSchedule(
            @RequestParam(required = false) Long dentistId,
            @RequestParam(required = false) String date,
            Model model
    ) {

        model.addAttribute("pageTitle", "Dentist's work schedule");
        model.addAttribute("staffName", "Staff");

        model.addAttribute("dentists", staffScheduleService.getAllDentists());

        model.addAttribute("selectedDentistId", dentistId);
        model.addAttribute("selectedDate", date);

        if (dentistId != null && date != null) {
            List<Appointment> schedule =
                    staffScheduleService.getDentistSchedule(
                            dentistId,
                            LocalDate.parse(date)
                    );
            model.addAttribute("schedule", schedule);
        }

        return "staff/schedules";
    }

    /* URL: /staff/schedules/dentist */
    @GetMapping("/dentist")
    @ResponseBody
    public List<Appointment> viewDentistSchedule(
            @RequestParam Long dentistId,
            @RequestParam String date
    ) {
        return staffScheduleService.getDentistSchedule(
                dentistId,
                LocalDate.parse(date)
        );
    }
    @GetMapping("/dentists")
    @ResponseBody
    public List<DentistProfile> getAllDentists() {
        return staffScheduleService.getAllDentists();
    }

}
