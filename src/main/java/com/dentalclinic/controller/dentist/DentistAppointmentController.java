package com.dentalclinic.controller.dentist;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;

@Controller
@RequestMapping("/dentist/appointments")
public class DentistAppointmentController {

    // ================= EXAMINATION =================

    @GetMapping("/{id}/examination")
    public String examinationPage(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            Model model
    ) {
        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", dentistUserId);

        model.addAttribute("patientName", "");
        model.addAttribute("apptDate", LocalDate.now());
        model.addAttribute("startTime", LocalTime.of(8, 0));
        model.addAttribute("endTime", LocalTime.of(9, 0));
        model.addAttribute("requestedServiceName", "");
        model.addAttribute("services", Collections.emptyList());
        model.addAttribute("historyRecords", Collections.emptyList());
        model.addAttribute("diagnosis", "");
        model.addAttribute("treatmentNote", "");

        return "Dentist/examination";
    }

    @PostMapping("/{id}/examination")
    public String saveExamination(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            @RequestParam String diagnosis,
            @RequestParam String treatmentNote,
            Model model
    ) {
        model.addAttribute("successMessage", "Saved successfully");

        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", dentistUserId);

        return "Dentist/examination";
    }

    // ================= BILLING =================

    @GetMapping("/{id}/billing-transfer")
    public String billingPage(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            Model model
    ) {
        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", dentistUserId);

        model.addAttribute("patientName", "");
        model.addAttribute("apptDate", LocalDate.now());
        model.addAttribute("startTime", LocalTime.of(8, 0));
        model.addAttribute("endTime", LocalTime.of(9, 0));
        model.addAttribute("requestedServiceName", "");
        model.addAttribute("services", Collections.emptyList());
        model.addAttribute("note", "");
        model.addAttribute("performedServicesJson", "[]");
        model.addAttribute("prescriptionNote", "[]");

        // ⚠️ QUAN TRỌNG: đúng tên file HTML của bạn
        return "Dentist/billing-note";
    }

    @PostMapping("/{id}/billing-transfer")
    public String saveBilling(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            @RequestParam(required = false) String note,
            @RequestParam(required = false) String performedServicesJson,
            @RequestParam(required = false) String prescriptionNote,
            Model model
    ) {
        model.addAttribute("successMessage", "Saved successfully");

        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", dentistUserId);

        // ⚠️ QUAN TRỌNG: đúng tên file HTML của bạn
        return "Dentist/billing-note";
    }
}
