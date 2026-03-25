package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.ServicesRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.dentist.DentistSessionService;
import com.dentalclinic.service.medical.MedicalRecordService;
import org.springframework.security.core.Authentication;
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
    private final UserRepository userRepository;

    public DentistAppointmentController(
            ServicesRepository servicesRepository,
            MedicalRecordService medicalRecordService,
            AppointmentRepository appointmentRepository,
            DentistSessionService dentistSessionService,
            UserRepository userRepository) {
        this.servicesRepository = servicesRepository;
        this.medicalRecordService = medicalRecordService;
        this.appointmentRepository = appointmentRepository;
        this.dentistSessionService = dentistSessionService;
        this.userRepository = userRepository;
    }

    /* ================= EXAMINATION ================= */

    @GetMapping("/{id}/examination")
    public String examinationPage(
            @PathVariable("id") Long id,
            @RequestParam("customerUserId") Long customerUserId,
            @RequestParam(value = "weekStart", required = false) String weekStart,
            Model model,
            RedirectAttributes redirect,
            Authentication authentication) {
        Long dentistUserId = resolveCurrentDentistUserId(authentication);

        Appointment appt;
        try {
            appt = dentistSessionService.loadOwnedAppointmentWithDetails(id, customerUserId, dentistUserId);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return buildWorkScheduleRedirect(weekStart);
        }
        if (appt.getStatus() == AppointmentStatus.CONFIRMED) {
            return buildWorkScheduleRedirect(weekStart);
        }
        // ðŸ”¥ Chỉ chuyển sang EXAMINING nếu chưa COMPLETED/WAITING_PAYMENT
        if (appt.getStatus() != AppointmentStatus.COMPLETED
                && appt.getStatus() != AppointmentStatus.WAITING_PAYMENT
                && appt.getStatus() != AppointmentStatus.EXAMINING) {

            appt.setStatus(AppointmentStatus.EXAMINING);
            appointmentRepository.save(appt);
        }

        model.addAttribute("weekStart", weekStart);
        model.addAttribute("appointmentId", id);
        model.addAttribute("customerUserId", customerUserId);
        model.addAttribute("dentistUserId", dentistUserId);
        model.addAttribute("patientName", appt.getCustomer().getFullName());
        model.addAttribute("apptDate", appt.getDate());
        model.addAttribute("startTime", appt.getStartTime());
        model.addAttribute("endTime", appt.getEndTime());
        model.addAttribute("requestedServiceName", buildServiceLabel(appt));
        model.addAttribute("appointmentNote", appt.getNotes());
        model.addAttribute("appointmentStatus", appt.getStatus().name());

        MedicalRecord record = medicalRecordService.findByAppointmentId(id).orElse(new MedicalRecord());
        record.setAppointment(appt);
        model.addAttribute("medicalRecord", record);
        model.addAttribute("historySteps", medicalRecordService.findReexamHistorySteps(id));

        return "Dentist/examination";
    }

    @PostMapping("/{id}/examination")
    public String saveExamination(
            @PathVariable("id") Long id,
            @RequestParam("customerUserId") Long customerUserId,
            @ModelAttribute MedicalRecord medicalRecord,
            @RequestParam(required = false) String weekStart, // âœ… THÊM
            RedirectAttributes redirect,
            Authentication authentication) {
        try {
            dentistSessionService.saveExam(
                    id,
                    customerUserId,
                    resolveCurrentDentistUserId(authentication),
                    medicalRecord);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return buildWorkScheduleRedirect(weekStart);
        }
        redirect.addFlashAttribute("successMessage", "Examination saved");

        return "redirect:/dentist/appointments/" + id +
                "/examination?customerUserId=" + customerUserId +
                "&weekStart=" + weekStart;
    }

    /* ================= BILLING ================= */

    @GetMapping("/{id}/billing-transfer")
    public String billingPage(
            @PathVariable("id") Long id,
            @RequestParam("customerUserId") Long customerUserId,
            @RequestParam(value = "weekStart", required = false) String weekStart,
            Model model,
            RedirectAttributes redirect,
            Authentication authentication) {
        Long dentistUserId = resolveCurrentDentistUserId(authentication);
        Appointment appt;
        try {
            appt = dentistSessionService.loadOwnedAppointmentWithDetails(id, customerUserId, dentistUserId);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return buildWorkScheduleRedirect(weekStart);
        }
        if (!isBillingViewAllowed(appt.getStatus())) {
            return buildWorkScheduleRedirect(weekStart);
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

        var billing = dentistSessionService.loadBilling(id, customerUserId, dentistUserId);
        model.addAttribute("billingNote", billing.billingNote());
        model.addAttribute("patientName", billing.patientName());

        return "Dentist/billing-note";
    }

    @PostMapping("/{id}/billing-transfer")
    public String saveBillingTransfer(
            @PathVariable("id") Long id,
            @RequestParam("customerUserId") Long customerUserId,
            @ModelAttribute BillingNote billingNote,
            @RequestParam("weekStart") String weekStart,
            RedirectAttributes redirect,
            Authentication authentication) {
        Long dentistUserId = resolveCurrentDentistUserId(authentication);
        Appointment appt;
        try {
            appt = dentistSessionService.loadOwnedAppointmentWithDetails(id, customerUserId, dentistUserId);
        } catch (IllegalArgumentException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return buildWorkScheduleRedirect(weekStart);
        }
        if (appt.getStatus() != AppointmentStatus.EXAMINING) {
            redirect.addFlashAttribute("errorMessage", "Chỉ được lưu khi đang khám.");
            return buildWorkScheduleRedirect(weekStart);
        }
        dentistSessionService.saveBilling(
                id,
                customerUserId,
                dentistUserId,
                billingNote);
        redirect.addFlashAttribute("successMessage", "Billing saved");
        return "redirect:/dentist/work-schedule?weekStart=" + weekStart;
    }

    private Long resolveCurrentDentistUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay tai khoan hien tai"))
                .getId();
    }

    private String buildWorkScheduleRedirect(String weekStart) {
        return "redirect:/dentist/work-schedule" + (weekStart != null ? "?weekStart=" + weekStart : "");
    }

    private boolean isBillingViewAllowed(AppointmentStatus status) {
        if (status == null) {
            return false;
        }
        return status == AppointmentStatus.EXAMINING
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
                            Comparator.nullsLast(Integer::compareTo)))
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
