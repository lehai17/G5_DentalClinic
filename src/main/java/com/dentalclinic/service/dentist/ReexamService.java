package com.dentalclinic.service.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentSlot;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.ServicesRepository;
import com.dentalclinic.repository.SlotRepository;
import com.dentalclinic.exception.BookingException;
import com.dentalclinic.exception.BookingErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.time.DayOfWeek;

@Service
public class ReexamService {
    
    private static final Logger logger = LoggerFactory.getLogger(ReexamService.class);
    
    private static final LocalTime CLINIC_OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLINIC_CLOSE = LocalTime.of(17, 0);
    private static final int SLOT_DURATION_MIN = 30;
    
    private final AppointmentRepository appointmentRepository;
    private final SlotRepository slotRepository;
    private final ServicesRepository servicesRepository;
    
    public ReexamService(AppointmentRepository appointmentRepository, SlotRepository slotRepository, ServicesRepository servicesRepository) {
        this.appointmentRepository = appointmentRepository;
        this.slotRepository = slotRepository;
        this.servicesRepository = servicesRepository;
    }
    
    /**
     * Check if reexam button should be enabled for an appointment
     */
    public boolean isReexamAvailable(AppointmentStatus status) {
        return status == AppointmentStatus.EXAMINING ||
               status == AppointmentStatus.DONE ||
               status == AppointmentStatus.COMPLETED ||
               status == AppointmentStatus.WAITING_PAYMENT;
    }
    
    /**
     * Check if page should be in read-only mode
     */
    public boolean isReadOnlyMode(AppointmentStatus status) {
        return status == AppointmentStatus.DONE ||
               status == AppointmentStatus.COMPLETED ||
               status == AppointmentStatus.WAITING_PAYMENT;
    }
    
    /**
     * Get existing reexam for an appointment, or null if not found
     */
    public Optional<Appointment> getExistingReexam(Long originalAppointmentId) {
        return appointmentRepository.findReexamByOriginalAppointmentId(originalAppointmentId);
    }
    
    /**
     * Create or update reexam appointment
     */
    @Transactional
    public Appointment createOrUpdateReexam(
            Long originalAppointmentId,
            LocalDate newDate,
            LocalTime newStartTime,
            LocalTime newEndTime,
            String notes,
            Long serviceId
    ) {
        // Load original appointment
        Appointment original = appointmentRepository.findById(originalAppointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, 
                    "Original appointment not found"));
        
        // Verify reexam is allowed
        if (!isReexamAvailable(original.getStatus())) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "Cannot create reexam for appointment with status: " + original.getStatus());
        }
        
        // Validate time
        validateReexamTime(original, newDate, newStartTime, newEndTime);
        
        // Check schedule conflict (excluding itself if updating)
        Optional<Appointment> existing = getExistingReexam(originalAppointmentId);
        if (existing.isPresent()) {
            // Update mode: check conflicts excluding this reexam
            checkDentistScheduleConflict(
                    original.getDentist().getId(),
                    newDate,
                    newStartTime,
                    newEndTime,
                    existing.get().getId()
            );
        } else {
            // Create mode: check conflicts normally
            checkDentistScheduleConflict(
                    original.getDentist().getId(),
                    newDate,
                    newStartTime,
                    newEndTime,
                    null
            );
        }
        
        if (existing.isPresent()) {
            // Update existing reexam
            Appointment reexam = existing.get();
            
            // Clear old slots
            List<Slot> oldSlots = reexam.getAppointmentSlots().stream()
                    .map(AppointmentSlot::getSlot)
                    .collect(Collectors.toList());
            reexam.clearAppointmentSlots();
            
            // Update time
            reexam.setDate(newDate);
            reexam.setStartTime(newStartTime);
            reexam.setEndTime(newEndTime);
            reexam.setNotes(notes);
            
            // Update service if provided
            if (serviceId != null) {
                Services service = servicesRepository.findById(serviceId)
                        .orElseThrow(() -> new BookingException(BookingErrorCode.VALIDATION_ERROR,
                                "Service not found"));
                reexam.setService(service);
            }
            
            // Reserve new slots
            int slotsNeeded = calculateSlotsNeeded(newStartTime, newEndTime);
            List<Slot> newSlots = reserveSlots(newDate, newStartTime, slotsNeeded);
            if (newSlots.isEmpty()) {
                throw new BookingException(BookingErrorCode.SLOT_FULL, "No available slots");
            }
            
            // Add new slots
            for (int i = 0; i < newSlots.size(); i++) {
                reexam.addAppointmentSlot(new AppointmentSlot(reexam, newSlots.get(i), i));
            }
            
            Appointment saved = appointmentRepository.save(reexam);
            
            // Release old slots
            releaseSlots(oldSlots);
            
            return saved;
        } else {
            // Create new reexam
            int slotsNeeded = calculateSlotsNeeded(newStartTime, newEndTime);
            List<Slot> slots = reserveSlots(newDate, newStartTime, slotsNeeded);
            if (slots.isEmpty()) {
                throw new BookingException(BookingErrorCode.SLOT_FULL, "No available slots");
            }
            
            Appointment reexam = new Appointment();
            reexam.setCustomer(original.getCustomer());
            reexam.setDentist(original.getDentist());
            
            // Set service: use provided serviceId if given, otherwise use original
            if (serviceId != null) {
                Services service = servicesRepository.findById(serviceId)
                        .orElseThrow(() -> new BookingException(BookingErrorCode.VALIDATION_ERROR,
                                "Service not found"));
                reexam.setService(service);
            } else {
                reexam.setService(original.getService());
            }
            
            reexam.setDate(newDate);
            reexam.setStartTime(newStartTime);
            reexam.setEndTime(newEndTime);
            reexam.setNotes(notes);
            reexam.setStatus(AppointmentStatus.REEXAM);
            reexam.setOriginalAppointment(original);
            reexam.setContactChannel(original.getContactChannel());
            reexam.setContactValue(original.getContactValue());
            
            for (int i = 0; i < slots.size(); i++) {
                reexam.addAppointmentSlot(new AppointmentSlot(reexam, slots.get(i), i));
            }
            
            return appointmentRepository.save(reexam);
        }
    }
    
    /**
     * Delete reexam appointment and free slots
     */
    @Transactional
    public void deleteReexam(Long appointmentId) {
        Appointment reexam = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND,
                    "Reexam appointment not found"));
        
        // Verify it's a reexam
        if (reexam.getOriginalAppointment() == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "This is not a reexam appointment");
        }
        
        // Verify original appointment status allows deletion
        Appointment original = reexam.getOriginalAppointment();
        if (original.getStatus() != AppointmentStatus.EXAMINING) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "Can only delete reexam when original appointment status is EXAMINING");
        }
        
        // Collect slots to release
        List<Slot> slots = reexam.getAppointmentSlots().stream()
                .map(AppointmentSlot::getSlot)
                .collect(Collectors.toList());
        
        // Clear slots
        reexam.clearAppointmentSlots();
        
        // Delete appointment
        appointmentRepository.delete(reexam);
        
        // Release slots
        releaseSlots(slots);
    }
    
    /**
     * Auto-confirm reexam when original appointment is done/completed
     * This is called automatically when the original appointment status changes
     * Uses REQUIRES_NEW to ensure immediate commit
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void autoConfirmReexamIfEligible(Long originalAppointmentId) {
        try {
            logger.info("[REEXAM] Checking for reexam to auto-confirm for appointment ID: {}", originalAppointmentId);
            
            // First check if reexam exists
            Optional<Appointment> reexamOpt = appointmentRepository.findReexamByOriginalAppointmentId(originalAppointmentId);
            
            if (reexamOpt.isPresent()) {
                Appointment reexam = reexamOpt.get();
                logger.info("[REEXAM] Found reexam with ID: {}, Current Status: {}", reexam.getId(), reexam.getStatus());
                
                // If reexam is in REEXAM status, auto-confirm it to CONFIRMED
                if (reexam.getStatus() == AppointmentStatus.REEXAM) {
                    logger.info("[REEXAM] Auto-confirming reexam ID: {}", reexam.getId());
                    
                    // Use native query to update directly in DB to bypass detached entity issues
                    int updated = appointmentRepository.updateReexamStatusToConfirmed(originalAppointmentId);
                    logger.info("[REEXAM] Updated {} reexam(s) status to CONFIRMED for original appointment ID: {}", updated, originalAppointmentId);
                } else {
                    logger.info("[REEXAM] Reexam ID: {} has status {} (not REEXAM), skipping auto-confirm", reexam.getId(), reexam.getStatus());
                }
            } else {
                logger.info("[REEXAM] No reexam found for original appointment ID: {}", originalAppointmentId);
            }
        } catch (Exception e) {
            logger.error("[REEXAM] Error auto-confirming reexam for appointment ID: {}", originalAppointmentId, e);
            e.printStackTrace();
        }
    }
    
    /**
     * Validate reexam time
     */
    public void validateReexamTime(Appointment originalAppointment, LocalDate date, LocalTime startTime, LocalTime endTime) {
        // Check if date is in past
        LocalDate today = LocalDate.now();
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SUNDAY) {
            throw new BookingException(
                    BookingErrorCode.VALIDATION_ERROR,
                    "Reexam appointments are only allowed from Monday to Saturday"
            );
        }
        if (date.isBefore(today)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "Cannot schedule reexam in the past");
        }
        
        // Check if today
        if (date.equals(today)) {
            LocalTime now = LocalTime.now();
            if (startTime.isBefore(now) || startTime.equals(now)) {
                throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                        "Start time must be in the future. Current time: " + now);
            }
        }
        
        // Check working hours
        if (startTime.isBefore(CLINIC_OPEN) || startTime.isAfter(CLINIC_CLOSE)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "Start time must be between 08:00 and 17:00");
        }
        
        if (endTime.isBefore(CLINIC_OPEN) || endTime.isAfter(CLINIC_CLOSE)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "End time must be between 08:00 and 17:00");
        }
        
        // Check start < end
        if (!startTime.isBefore(endTime)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "Start time must be before end time");
        }
        
        // Check 30 minute intervals
        if (!isValid30MinInterval(startTime)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "Start time must be at 30-minute intervals (e.g., 08:00, 08:30, 09:00)");
        }
        
        if (!isValid30MinInterval(endTime)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "End time must be at 30-minute intervals (e.g., 08:00, 08:30, 09:00)");
        }

        if (originalAppointment != null
                && originalAppointment.getDate() != null
                && originalAppointment.getStartTime() != null
                && originalAppointment.getDate().isEqual(date)
                && startTime.isBefore(originalAppointment.getStartTime())) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                    "Reexam start time cannot be earlier than the original appointment start time on the same day");
        }
    }
    
    /**
     * Check if time is at 30-minute intervals
     */
    private boolean isValid30MinInterval(LocalTime time) {
        return time.getMinute() == 0 || time.getMinute() == 30;
    }
    
    /**
     * Check dentist schedule conflicts
     */
    public void checkDentistScheduleConflict(
            Long dentistId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            Long excludeAppointmentId
    ) {
        int conflictCount;
        if (excludeAppointmentId != null) {
            conflictCount = appointmentRepository.checkOverlappingAppointmentExcludingSelf(
                    dentistId, date, startTime, endTime, excludeAppointmentId);
        } else {
            conflictCount = appointmentRepository.checkOverlappingAppointment(
                    dentistId, date, startTime, endTime);
        }
        
        if (conflictCount > 0) {
            throw new BookingException(BookingErrorCode.SLOT_OVERLAP,
                    "Dentist has conflicting appointments at this time");
        }
    }
    
    /**
     * Calculate slots needed for duration
     */
    private int calculateSlotsNeeded(LocalTime startTime, LocalTime endTime) {
        long minutes = java.time.temporal.ChronoUnit.MINUTES.between(startTime, endTime);
        return (int) Math.ceil((double) minutes / SLOT_DURATION_MIN);
    }
    
    /**
     * Reserve slots for appointment
     */
    private List<Slot> reserveSlots(LocalDate date, LocalTime startTime, int slotsNeeded) {
        LocalDateTime startDateTime = LocalDateTime.of(date, startTime);
        LocalDateTime endDateTime = startDateTime.plusMinutes((long) slotsNeeded * SLOT_DURATION_MIN);
        
        List<Slot> locked = slotRepository.findActiveSlotsForUpdate(startDateTime, endDateTime);
        if (locked.size() != slotsNeeded) return new ArrayList<>();
        
        for (int i = 0; i < locked.size(); i++) {
            if (!locked.get(i).getSlotTime().equals(startDateTime.plusMinutes((long) i * SLOT_DURATION_MIN)) || !locked.get(i).isAvailable()) {
                return new ArrayList<>();
            }
        }
        
        for (Slot s : locked) {
            s.setBookedCount(s.getBookedCount() + 1);
            slotRepository.save(s);
        }
        
        return locked;
    }
    
    /**
     * Release slots
     */
    private void releaseSlots(List<Slot> slots) {
        for (Slot s : slots) {
            Slot locked = slotRepository.findBySlotTimeAndActiveTrueForUpdate(s.getSlotTime())
                    .orElse(null);
            if (locked != null && locked.getBookedCount() > 0) {
                locked.setBookedCount(locked.getBookedCount() - 1);
                slotRepository.save(locked);
            }
        }
    }
}
