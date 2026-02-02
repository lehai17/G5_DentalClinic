package com.dentalclinic.controller.dentist;

import com.dentalclinic.repository.ServicesRepository;
import com.dentalclinic.model.service.Services;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/dentist/appointments")
public class DentistAppointmentController {

    private final ServicesRepository servicesRepository;

    public DentistAppointmentController(ServicesRepository servicesRepository) {
        this.servicesRepository = servicesRepository;
    }

    // ================= EXAMINATION =================

    @GetMapping("/{id}/examination")
    public String examinationPage(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            Model model
    ) {
        List<Services> services = servicesRepository.findAll();

        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", dentistUserId);

        model.addAttribute("patientName", "");
        model.addAttribute("apptDate", LocalDate.now());
        model.addAttribute("startTime", LocalTime.of(8, 0));
        model.addAttribute("endTime", LocalTime.of(9, 0));
        model.addAttribute("requestedServiceName", "");

        // 🔥 QUAN TRỌNG
        model.addAttribute("services", services);

        model.addAttribute("historyRecords", List.of());
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
            RedirectAttributes redirect
    ) {
        // TODO: gọi service lưu MedicalRecord

        redirect.addFlashAttribute("successMessage", "Examination saved successfully");
        return "redirect:/dentist/appointments/" + id
                + "/examination?customerUserId=" + customerUserId
                + "&dentistUserId=" + dentistUserId;
    }


    // ================= BILLING =================

    @GetMapping("/{id}/billing-transfer")
    public String billingPage(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            Model model
    ) {
        List<Services> services = servicesRepository.findAll();

        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", dentistUserId);

        model.addAttribute("patientName", "");
        model.addAttribute("apptDate", LocalDate.now());
        model.addAttribute("startTime", LocalTime.of(8, 0));
        model.addAttribute("endTime", LocalTime.of(9, 0));
        model.addAttribute("requestedServiceName", "");

        // 🔥 QUAN TRỌNG
        model.addAttribute("services", services);

        model.addAttribute("note", "");
        model.addAttribute("performedServicesJson", "[]");
        model.addAttribute("prescriptionNote", "[]");

        return "Dentist/billing-note";
    }
    @PostMapping("/{id}/billing-transfer")
    public String saveBillingTransfer(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            @RequestParam String note,
            @RequestParam String performedServicesJson,
            @RequestParam String prescriptionNote,
            RedirectAttributes redirect
    ) {
        // TODO: gọi service xử lý billing

        redirect.addFlashAttribute("successMessage", "Billing note saved successfully");
        return "redirect:/dentist/appointments/" + id
                + "/billing-transfer?customerUserId=" + customerUserId
                + "&dentistUserId=" + dentistUserId;
    }

}
