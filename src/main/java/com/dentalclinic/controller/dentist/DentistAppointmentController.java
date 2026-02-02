package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.ServicesRepository;
import com.dentalclinic.service.medical.MedicalRecordService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/dentist/appointments")
public class DentistAppointmentController {

    private final ServicesRepository servicesRepository;
    private final MedicalRecordService medicalRecordService;
    private final AppointmentRepository appointmentRepository;
    public DentistAppointmentController(
            ServicesRepository servicesRepository,
            MedicalRecordService medicalRecordService,
            AppointmentRepository appointmentRepository
    ) {
        this.servicesRepository = servicesRepository;
        this.medicalRecordService = medicalRecordService;
        this.appointmentRepository = appointmentRepository;
    }

    // ================= EXAMINATION =================

    /**
     * VIEW examination page
     * - Nếu đã có MedicalRecord → load lại dữ liệu
     * - Nếu chưa có → form trống
     */
    @GetMapping("/{id}/examination")
    public String examinationPage(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            Model model
    ) {
        // ===== services list (cho propose service)
        List<Services> services = servicesRepository.findAll();
        model.addAttribute("services", services);

        // ===== basic appointment info (demo / tạm)
        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", dentistUserId);

        model.addAttribute("patientName", "");
        model.addAttribute("apptDate", LocalDate.now());
        model.addAttribute("startTime", LocalTime.of(8, 0));
        model.addAttribute("endTime", LocalTime.of(9, 0));
        model.addAttribute("requestedServiceName", "");

        // ===== LOAD medical record nếu đã tồn tại
        MedicalRecord record =
                medicalRecordService
                        .findByAppointmentId(id)
                        .orElse(null);

        if (record != null) {
            model.addAttribute("diagnosis", record.getDiagnosis());
            model.addAttribute("treatmentNote", record.getTreatmentNote());
        } else {
            model.addAttribute("diagnosis", "");
            model.addAttribute("treatmentNote", "");
        }

        // ===== history (sau làm tiếp)
        model.addAttribute("historyRecords", List.of());

        return "Dentist/examination";
    }

    /**
     * SAVE examination
     * - UPSERT theo appointment
     * - KHÔNG tạo record mới nếu đã tồn tại
     */
    @PostMapping("/{id}/examination")
    public String saveExamination(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @RequestParam Long dentistUserId,
            @RequestParam String diagnosis,
            @RequestParam String treatmentNote,
            RedirectAttributes redirect
    ) {
        medicalRecordService.saveOrUpdate(
                id,
                diagnosis,
                treatmentNote
        );

        redirect.addFlashAttribute(
                "successMessage",
                "Examination saved successfully"
        );

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
        // 🔒 KHÓA APPOINTMENT SAU KHI BILLING
        Appointment appointment = appointmentRepository
                .findById(id)
                .orElseThrow();

        appointment.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appointment);

        redirect.addFlashAttribute(
                "successMessage",
                "Billing note saved successfully"
        );

        return "redirect:/dentist/work-schedule";
    }


}
