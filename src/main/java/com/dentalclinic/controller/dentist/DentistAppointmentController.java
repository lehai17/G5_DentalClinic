package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.ServicesRepository;
import com.dentalclinic.service.dentist.DentistSessionService;
import com.dentalclinic.service.medical.MedicalRecordService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.payment.BillingNote;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;


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


        Appointment appt = appointmentRepository.findByIdWithDetails(id).orElseThrow();
        if (appt.getStatus() == AppointmentStatus.CONFIRMED) {
            return "redirect:/dentist/work-schedule" + (weekStart != null ? "?weekStart=" + weekStart : "");
        }
        // ðŸ”¥ Chỉ chuyển sang EXAMINING nếu chưa DONE/COMPLETED/WAITING_PAYMENT 
        if (appt.getStatus() != AppointmentStatus.DONE
                && appt.getStatus() != AppointmentStatus.COMPLETED
                && appt.getStatus() != AppointmentStatus.WAITING_PAYMENT
                && appt.getStatus() != AppointmentStatus.EXAMINING) {

            appt.setStatus(AppointmentStatus.EXAMINING);
            appointmentRepository.save(appt);
        }

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", appt.getDentist() != null ? appt.getDentist().getUser().getId() : "");
        model.addAttribute("patientName", appt.getCustomer().getFullName());
        model.addAttribute("apptDate", appt.getDate());
        model.addAttribute("startTime", appt.getStartTime());
        model.addAttribute("endTime", appt.getEndTime());
        model.addAttribute("requestedServiceName", buildServiceLabel(appt));
        model.addAttribute("appointmentNote", appt.getNotes());
        model.addAttribute("appointmentStatus", appt.getStatus().name());

        MedicalRecord record =
                medicalRecordService.findByAppointmentId(id).orElse(new MedicalRecord());
        record.setAppointment(appt);
        model.addAttribute("medicalRecord", record);
        model.addAttribute(
                "selectedServiceIds",
                record.getProposedServices()
                        .stream()
                        .filter(ps -> ps.getService() != null)
                        .map(ps -> ps.getService().getId())
                        .toList()
        );
        // history records for past exams
        model.addAttribute("historyRecords", java.util.Collections.emptyList());

        return "Dentist/examination";
    }

    @PostMapping("/{id}/examination")
    public String saveExamination(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @ModelAttribute MedicalRecord medicalRecord,
            @RequestParam(required = false) String weekStart, // âœ… THÊM
            RedirectAttributes redirect
    ) {
        dentistSessionService.saveExam(
                id,
                customerUserId,
                medicalRecord
        );
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
        Appointment appt = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        if (!isBillingViewAllowed(appt.getStatus())) {
            return "redirect:/dentist/work-schedule" + (weekStart != null ? "?weekStart=" + weekStart : "");
        }

        model.addAttribute("services", servicesRepository.findAll());
        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("patientName", appt.getCustomer().getFullName());
        model.addAttribute("apptDate", appt.getDate());
        model.addAttribute("startTime", appt.getStartTime());
        model.addAttribute("endTime", appt.getEndTime());
        model.addAttribute("requestedServiceName", buildServiceLabel(appt));
        model.addAttribute("appointmentStatus", appt.getStatus().name());
        model.addAttribute("weekStart", weekStart);

        var billing = dentistSessionService.loadBilling(id, customerUserId);
        model.addAttribute("billingNote", billing.billingNote());
        model.addAttribute("patientName", billing.patientName());

        return "Dentist/billing-note";
    }

    @PostMapping("/{id}/billing-transfer")
    public String saveBillingTransfer(
            @PathVariable Long id,
            @RequestParam Long customerUserId,
            @ModelAttribute BillingNote billingNote,
            @RequestParam String weekStart,
            RedirectAttributes redirect
    ) {
        Appointment appt = appointmentRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        if (appt.getStatus() != AppointmentStatus.EXAMINING) {
            redirect.addFlashAttribute("errorMessage", "Chỉ được lưu khi đang khám.");
            return "redirect:/dentist/work-schedule" + (weekStart != null ? "?weekStart=" + weekStart : "");
        }
        dentistSessionService.saveBilling(
                id,
                customerUserId,
                billingNote
        );
        redirect.addFlashAttribute("successMessage", "Billing saved");
        return "redirect:/dentist/work-schedule?weekStart=" + weekStart;
    }

    private boolean isBillingViewAllowed(AppointmentStatus status) {
        if (status == null) {
            return false;
        }
        return status == AppointmentStatus.EXAMINING
                || status == AppointmentStatus.DONE
                || status == AppointmentStatus.WAITING_PAYMENT
                || status == AppointmentStatus.COMPLETED;
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
}

