package com.dentalclinic.controller.dentist;

import com.dentalclinic.exception.BookingException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.service.dentist.ReexamService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Controller
@RequestMapping("/dentist/reexam")
public class ReexamController {
    
    private final ReexamService reexamService;
    private final AppointmentRepository appointmentRepository;
    
    public ReexamController(ReexamService reexamService, AppointmentRepository appointmentRepository) {
        this.reexamService = reexamService;
        this.appointmentRepository = appointmentRepository;
    }
    
    /**
     * Show reexam form for creating or editing
     */
    @GetMapping("/{appointmentId}")
    public String reexamForm(
            @PathVariable Long appointmentId,
            @RequestParam(required = false) String weekStart,
            Model model
    ) {
        Appointment original = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
        
        // Check if reexam is available
        if (!reexamService.isReexamAvailable(original.getStatus())) {
            throw new RuntimeException("Cannot create reexam for this appointment status");
        }
        
        Optional<Appointment> existingReexam = reexamService.getExistingReexam(appointmentId);
        boolean isReadOnly = reexamService.isReadOnlyMode(original.getStatus());
        boolean isUpdate = existingReexam.isPresent();
        
        Appointment reexam = existingReexam.orElse(new Appointment());
        
        // If not found, pre-fill with original appointment data
        if (!isUpdate) {
            reexam.setCustomer(original.getCustomer());
            reexam.setDentist(original.getDentist());
            reexam.setService(original.getService());

        }
        
        // Set model attributes
        model.addAttribute("originalAppointmentId", appointmentId);
        model.addAttribute("originalAppointmentStatus", original.getStatus().name());
        model.addAttribute("reexam", reexam);
        model.addAttribute("isUpdate", isUpdate);
        model.addAttribute("isReadOnly", isReadOnly);
        model.addAttribute("originalAppointment", original);
        model.addAttribute("weekStart", weekStart);
        
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
            @RequestParam(required = false) String weekStart,
            RedirectAttributes redirect
    ) {
        try {
            Appointment saved = reexamService.createOrUpdateReexam(
                    appointmentId,
                    date,
                    startTime,
                    endTime,
                    notes
            );
            
            String msg = reexamService.getExistingReexam(appointmentId).isPresent() 
                    ? "Reexam updated successfully"
                    : "Reexam created successfully";
            redirect.addFlashAttribute("successMessage", msg);
            
        } catch (BookingException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/dentist/reexam/" + appointmentId + 
                    (weekStart != null ? "?weekStart=" + weekStart : "");
        }
        
        redirect.addAttribute("weekStart", weekStart);
        return "redirect:/dentist/work-schedule" + 
                (weekStart != null ? "?weekStart=" + weekStart : "");
    }
    
    /**
     * Delete reexam
     */
    @PostMapping("/delete/{reexamId}")
    public String deleteReexam(
            @PathVariable Long reexamId,
            @RequestParam(required = false) String weekStart,
            RedirectAttributes redirect
    ) {
        try {
            reexamService.deleteReexam(reexamId);
            redirect.addFlashAttribute("successMessage", "Reexam deleted successfully");
        } catch (BookingException e) {
            redirect.addFlashAttribute("errorMessage", e.getMessage());
        } catch (Exception e) {
            redirect.addFlashAttribute("errorMessage", "Error deleting reexam");
        }
        
        return "redirect:/dentist/work-schedule" + 
                (weekStart != null ? "?weekStart=" + weekStart : "");
    }
    
    /**
     * Get available time slots as JSON (for AJAX)
     */
    @GetMapping("/slots/{appointmentId}")
    @ResponseBody
    public ReexamSlotsResponse getAvailableSlots(
            @PathVariable Long appointmentId,
            @RequestParam LocalDate date
    ) {
        try {
            Appointment original = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Appointment not found"));
            
            // Get existing reexam if updating - exclude it from conflict check
            Optional<Appointment> existingReexam = reexamService.getExistingReexam(appointmentId);
            Long reexamIdToExclude = existingReexam.isPresent() ? existingReexam.get().getId() : null;
            
            // Generate time slots for the day (30-minute intervals)
            ReexamSlotsResponse response = new ReexamSlotsResponse();
            
            LocalTime current = LocalTime.of(8, 0);
            LocalTime end = LocalTime.of(17, 0);
            
            while (!current.isAfter(end)) {
                // Check if this time is available (no conflicts)
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
        private java.util.List<String> availableSlots = new java.util.ArrayList<>();
        
        public void addAvailableSlot(String slot) {
            availableSlots.add(slot);
        }
        
        public java.util.List<String> getAvailableSlots() {
            return availableSlots;
        }
    }
}
