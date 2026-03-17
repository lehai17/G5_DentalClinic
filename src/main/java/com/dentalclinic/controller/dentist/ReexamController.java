package com.dentalclinic.controller.dentist;

import com.dentalclinic.exception.BookingException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.repository.ServicesRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.dentist.ReexamService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/dentist/reexam")
public class ReexamController {
    private final ReexamService reexamService;
    private final ServicesRepository servicesRepository;
    private final UserRepository userRepository;

    public ReexamController(ReexamService reexamService,
                            ServicesRepository servicesRepository,
                            UserRepository userRepository) {
        this.reexamService = reexamService;
        this.servicesRepository = servicesRepository;
        this.userRepository = userRepository;
    }

    /**
     * Show reexam form for creating or editing
     */
    @GetMapping("/{appointmentId}")
    public String reexamForm(
            @PathVariable Long appointmentId,
            @RequestParam(required = false) String weekStart,
            Model model,
            RedirectAttributes redirect,
            Authentication authentication
    ) {
        Long dentistUserId = resolveCurrentDentistUserId(authentication);
        Appointment original;
        try {
            original = reexamService.loadOwnedOriginalAppointmentWithDetails(appointmentId, dentistUserId);
        } catch (BookingException ex) {
            redirect.addFlashAttribute("errorMessage", ex.getMessage());
            return buildWorkScheduleRedirect(weekStart);
        }

        if (!reexamService.isReexamAvailable(original.getStatus())) {
            redirect.addFlashAttribute("errorMessage", "Cannot create reexam for this appointment status");
            return buildWorkScheduleRedirect(weekStart);
        }

        Optional<Appointment> existingReexam =
                reexamService.getExistingReexamForDentist(appointmentId, dentistUserId);
        boolean isUpdate = existingReexam.isPresent();

        Appointment reexam = existingReexam.orElse(new Appointment());
        if (!isUpdate) {
            reexam.setCustomer(original.getCustomer());
            reexam.setDentist(original.getDentist());
            reexam.setService(original.getService());
        }

        String originalServiceLabel = buildServiceLabel(original);
        boolean preferPlaceholder = shouldPreferPlaceholder(original, isUpdate);

        model.addAttribute("originalAppointmentId", appointmentId);
        model.addAttribute("originalAppointmentStatus", original.getStatus().name());
        model.addAttribute("reexam", reexam);
        model.addAttribute("isUpdate", isUpdate);
        model.addAttribute("originalAppointment", original);
        model.addAttribute("originalServiceLabel", originalServiceLabel);
        model.addAttribute("preferServicePlaceholder", preferPlaceholder);
        model.addAttribute("weekStart", weekStart);
        model.addAttribute("services", servicesRepository.findAll());

        return "Dentist/reexam-form";
    }

    /**
     * Save reexam (create or update)
     */
    @PostMapping("/save/{appointmentId}")
    public String saveReexam(
            @PathVariable Long appointmentId,
            @RequestParam LocalDate date,
            @RequestParam LocalTime startTime,
            @RequestParam LocalTime endTime,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String weekStart,
            RedirectAttributes redirect,
            Authentication authentication
    ) {
        Long dentistUserId = resolveCurrentDentistUserId(authentication);
        try {
            boolean existedBefore =
                    reexamService.getExistingReexamForDentist(appointmentId, dentistUserId).isPresent();

            reexamService.createOrUpdateReexam(
                    appointmentId,
                    dentistUserId,
                    date,
                    startTime,
                    endTime,
                    notes,
                    serviceId
            );

            String msg = existedBefore
                    ? "Reexam updated successfully"
                    : "Reexam created successfully";
            redirect.addFlashAttribute("successMessage", msg);

        } catch (BookingException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/dentist/reexam/" + appointmentId
                    + (weekStart != null ? "?weekStart=" + weekStart : "");
        }

        return "redirect:/dentist/reexam/" + appointmentId
                + (weekStart != null ? "?weekStart=" + weekStart : "");
    }

    /**
     * Delete reexam
     */
    @PostMapping("/delete/{reexamId}")
    public String deleteReexam(
            @PathVariable Long reexamId,
            @RequestParam(required = false) String weekStart,
            RedirectAttributes redirect,
            Authentication authentication
    ) {
        try {
            reexamService.deleteReexam(reexamId, resolveCurrentDentistUserId(authentication));
            redirect.addFlashAttribute("successMessage", "Reexam deleted successfully");
        } catch (BookingException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", "Error deleting reexam");
        }

        return buildWorkScheduleRedirect(weekStart);
    }

    /**
     * Get available time slots as JSON (for AJAX)
     */
    @GetMapping("/slots/{appointmentId}")
    @ResponseBody
    public ReexamSlotsResponse getAvailableSlots(
            @PathVariable Long appointmentId,
            @RequestParam LocalDate date,
            Authentication authentication
    ) {
        try {
            Long dentistUserId = resolveCurrentDentistUserId(authentication);
            Appointment original = reexamService.loadOwnedOriginalAppointmentWithDetails(appointmentId, dentistUserId);

            Optional<Appointment> existingReexam =
                    reexamService.getExistingReexamForDentist(appointmentId, dentistUserId);
            Long reexamIdToExclude = existingReexam.map(Appointment::getId).orElse(null);

            ReexamSlotsResponse response = new ReexamSlotsResponse();
            LocalTime current = LocalTime.of(8, 0);
            LocalTime end = LocalTime.of(17, 0);

            while (!current.isAfter(end)) {
                try {
                    LocalTime slotEnd = current.plusMinutes(30);
                    if (!slotEnd.isAfter(end)) {
                        reexamService.checkDentistScheduleConflict(
                                original.getDentist().getId(),
                                date,
                                current,
                                slotEnd,
                                reexamIdToExclude
                        );
                        response.addAvailableSlot(current.toString());
                    }
                } catch (BookingException e) {
                    // Slot not available
                }

                current = current.plusMinutes(30);
            }

            return response;

        } catch (Exception e) {
            return new ReexamSlotsResponse();
        }
    }

    /**
     * DTO for available slots response
     */
    public static class ReexamSlotsResponse {
        private final java.util.List<String> availableSlots = new java.util.ArrayList<>();

        public void addAvailableSlot(String slot) {
            availableSlots.add(slot);
        }

        public java.util.List<String> getAvailableSlots() {
            return availableSlots;
        }
    }

    private Long resolveCurrentDentistUserId(Authentication authentication) {
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay tai khoan hien tai"))
                .getId();
    }

    private String buildWorkScheduleRedirect(String weekStart) {
        return "redirect:/dentist/work-schedule" + (weekStart != null ? "?weekStart=" + weekStart : "");
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

    private boolean shouldPreferPlaceholder(Appointment original, boolean isUpdate) {
        if (isUpdate || original == null) {
            return false;
        }

        List<AppointmentDetail> details = original.getAppointmentDetails();
        return details != null && details.size() > 1;
    }
}
