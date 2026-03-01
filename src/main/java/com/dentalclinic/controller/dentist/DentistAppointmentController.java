package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.ServicesRepository;
import com.dentalclinic.service.dentist.DentistSessionService;
import com.dentalclinic.service.medical.MedicalRecordService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/dentist/appointments")
public class DentistAppointmentController {

    private final ServicesRepository servicesRepository;
    private final MedicalRecordService medicalRecordService;
    private final AppointmentRepository appointmentRepository;
    private final DentistSessionService dentistSessionService;

    public DentistAppointmentController(
            ServicesRepository servicesRepository,
            MedicalRecordService medicalRecordService,
            AppointmentRepository appointmentRepository,
            DentistSessionService dentistSessionService
    ) {
        this.servicesRepository = servicesRepository;
        this.medicalRecordService = medicalRecordService;
        this.appointmentRepository = appointmentRepository;
        this.dentistSessionService = dentistSessionService;
    }

    /* ================= EXAMINATION ================= */

    @GetMapping("/{id}/examination")
    public String examinationPage(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam(required = false) String weekStart,
            Model model
    ) {
        model.addAttribute("services", servicesRepository.findAll());

        Appointment appt = appointmentRepository.findById(id).orElseThrow();

        // CHUYỂN SANG IN_PROGRESS
        if (appt.getStatus().name().equals("CONFIRMED")) {
            appt.setStatus(
                    com.dentalclinic.model.appointment.AppointmentStatus.IN_PROGRESS
            );
            appointmentRepository.save(appt);
        }

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("patientName", appt.getCustomer().getFullName());
        model.addAttribute("apptDate", appt.getDate());
        model.addAttribute("startTime", appt.getStartTime());
        model.addAttribute("endTime", appt.getEndTime());
        model.addAttribute("requestedServiceName", appt.getService().getName());
        model.addAttribute("appointmentStatus", appt.getStatus().name());

        MedicalRecord record =
                medicalRecordService.findByAppointmentId(id).orElse(null);

        model.addAttribute("diagnosis", record == null ? "" : record.getDiagnosis());
        model.addAttribute("treatmentNote", record == null ? "" : record.getTreatmentNote());
        model.addAttribute("historyRecords", List.of());

        return "Dentist/examination";
    }

    @PostMapping("/{id}/examination")
    public String saveExamination(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam String diagnosis,
            @RequestParam String treatmentNote,
            @RequestParam(required = false) String weekStart, // ✅ THÊM
            RedirectAttributes redirect
    ) {
        medicalRecordService.saveOrUpdate(id, diagnosis, treatmentNote);
        redirect.addFlashAttribute("successMessage", "Examination saved");

        return "redirect:/dentist/appointments/" + id +
                "/examination?customerUserId=" + customerUserId +
                "&weekStart=" + weekStart;
    }

    /* ================= BILLING ================= */

    @GetMapping("/{id}/billing-transfer")
    public String billingPage(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam(required = false) String weekStart,
            Model model
    ) {
        Appointment appt = appointmentRepository.findById(id).orElseThrow();

        model.addAttribute("services", servicesRepository.findAll());
        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("patientName", appt.getCustomer().getFullName());
        model.addAttribute("apptDate", appt.getDate());
        model.addAttribute("startTime", appt.getStartTime());
        model.addAttribute("endTime", appt.getEndTime());
        model.addAttribute("requestedServiceName", appt.getService().getName());
        model.addAttribute("appointmentStatus", appt.getStatus().name());
        model.addAttribute("weekStart", weekStart);

        var billing = dentistSessionService.loadBilling(id, customerUserId);
        model.addAttribute("note", billing.note());
        model.addAttribute("performedServicesJson", billing.performedServicesJson());
        model.addAttribute("prescriptionNote", billing.prescriptionNote());

        return "Dentist/billing-note";
    }

    @PostMapping("/{id}/billing-transfer")
    public String saveBillingTransfer(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam String note,
            @RequestParam String performedServicesJson,
            @RequestParam String prescriptionNote,
            @RequestParam String weekStart,
            RedirectAttributes redirect
    ) {
        dentistSessionService.saveBilling(
                id,
                customerUserId,
                performedServicesJson,
                prescriptionNote,
                note
        );

        redirect.addFlashAttribute("successMessage", "Billing saved");
        return "redirect:/dentist/work-schedule?weekStart=" + weekStart;
    }
}
