package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentSlot;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.SlotRepository;
import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CustomerAppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(CustomerAppointmentService.class);

    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern VN_PHONE_PATTERN =
            Pattern.compile("^(0|\\+84)\\d{9,10}$");

        // clinic hours and slot constants (previously in BookingService)
        private static final LocalTime CLINIC_OPEN_TIME = LocalTime.of(8, 0);
        private static final LocalTime CLINIC_CLOSE_TIME = LocalTime.of(17, 0);
        private static final int SLOT_DURATION_MINUTES = 30;
        private static final int SLOTS_PER_DAY = 18;

    public CustomerAppointmentService(CustomerProfileRepository customerProfileRepository,
                                       UserRepository userRepository,
                                       AppointmentRepository appointmentRepository,
                                       ServiceRepository serviceRepository,
                                       SlotRepository slotRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
    }

    private CustomerProfile getOrCreateCustomerProfile(Long userId) {
        return customerProfileRepository.findByUser_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new IllegalArgumentException("Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại."));
                    CustomerProfile profile = new CustomerProfile();
                    profile.setUser(user);
                    profile.setFullName(user.getEmail() != null ? user.getEmail() : "Khách hàng");
                    return customerProfileRepository.save(profile);
                });
    }

    private void validateCreateRequest(CreateAppointmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đặt lịch không hợp lệ.");
        }
        
        if (request.getServiceId() == null || request.getServiceId() <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn dịch vụ hợp lệ.");
        }

        // Support both old format (slotId) and new format (selectedDate + selectedTime)
        boolean hasOldFormat = request.isOldFormat();
        boolean hasNewFormat = request.isNewFormat();
        
        if (!hasOldFormat && !hasNewFormat) {
            throw new IllegalArgumentException("Vui lòng chọn ngày và giờ hợp lệ.");
        }

        if (request.getPatientNote() != null && request.getPatientNote().length() > 500) {
            throw new IllegalArgumentException("Ghi chú tối đa 500 ký tự.");
        }

        String channel = request.getContactChannel();
        String value = request.getContactValue();

        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("Vui lòng chọn kênh liên hệ.");
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập thông tin liên hệ.");
        }

        channel = channel.trim().toUpperCase();
        value = value.trim();

        switch (channel) {
            case "PHONE":
            case "ZALO": {
                String v = value.replaceAll("\\s+", "");
                if (!VN_PHONE_PATTERN.matcher(v).matches()) {
                    throw new IllegalArgumentException("Số điện thoại/Zalo không hợp lệ (VD: 0xxxxxxxxx hoặc +84xxxxxxxxx).");
                }
                break;
            }
            case "EMAIL": {
                if (!EMAIL_PATTERN.matcher(value).matches()) {
                    throw new IllegalArgumentException("Email không hợp lệ.");
                }
                break;
            }
            default:
                throw new IllegalArgumentException("Kênh liên hệ chỉ được: PHONE, ZALO, EMAIL.");
        }
    }

    /**
     * Get available slots for a given date.
     * Returns slots that have enough consecutive available slots for the selected service.
     */
    public List<SlotDto> getAvailableSlots(Long serviceId, LocalDate date) {
        // determine how many 30‑minute slots the service requires
        Services service = null;
        int durationMinutes = 30;

        if (serviceId != null) {
            service = serviceRepository.findById(serviceId).orElse(null);
            if (service != null) {
                durationMinutes = service.getDurationMinutes();
            }
        }

        int slotsNeeded = calculateSlotsNeeded(durationMinutes);

        /*
         * NEW LOGIC: return every slot that the booking page should display and
         * compute the number of free spots for the selected service at that
         * start time.  The previous implementation filtered out start times
         * where the consecutive window was missing or one of the slots was
         * already full, which resulted in "no slots" being sent back when the
         * database contained any fully-booked time in the middle of the day.
         *
         * By delivering all (future) slots with an `availableSpots` count, the
         * frontend can show "Còn X chỗ" or "Hết chỗ" and the user still can
         * only select times where at least one spot remains.
         */

        List<Slot> allSlots = fetchAllSlotsForDate(date);

        List<SlotDto> result = allSlots.stream().map(slot -> {
            SlotDto dto = new SlotDto();
            dto.setId(slot.getId());
            dto.setDate(slot.getSlotTime().toLocalDate());
            dto.setStartTime(slot.getSlotTime().toLocalTime());
            dto.setEndTime(slot.getSlotTime().toLocalTime().plusMinutes(30));
            dto.setCapacity(slot.getCapacity());
            dto.setBookedCount(slot.getBookedCount());

            // compute minimum available spots across the required window
            int minSpots = slot.getAvailableSpots();
            for (int j = 1; j < slotsNeeded; j++) {
                int idx = allSlots.indexOf(slot) + j;
                if (idx >= allSlots.size()) {
                    // window exceeds end of day
                    minSpots = 0;
                    break;
                }
                Slot next = allSlots.get(idx);
                minSpots = Math.min(minSpots, next.getAvailableSpots());
            }

            dto.setAvailableSpots(minSpots);
            dto.setAvailable(minSpots > 0);
            return dto;
        }).collect(Collectors.toList());

        return result;
    }

    /**
     * Get all slots for a given date with their availability status and overlap detection.
     */
    public List<SlotDto> getAllSlotsForDate(LocalDate date) {
        List<Slot> slots = fetchAllSlotsForDate(date);

        return slots.stream()
                .map(slot -> {
                    SlotDto dto = new SlotDto();
                    dto.setId(slot.getId());
                    dto.setDate(slot.getSlotTime().toLocalDate());
                    dto.setStartTime(slot.getSlotTime().toLocalTime());
                    dto.setEndTime(slot.getSlotTime().toLocalTime().plusMinutes(30));
                    dto.setAvailable(slot.isAvailable());
                    dto.setCapacity(slot.getCapacity());
                    dto.setBookedCount(slot.getBookedCount());
                    dto.setAvailableSpots(slot.getAvailableSpots());
                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get available slots with overlap status for the current user.
     * Include information about which slots the user already has bookings for.
     */
    public List<SlotDto> getAvailableSlotsWithOverlapStatus(Long userId, Long serviceId, LocalDate date) {
        List<SlotDto> slots = getAvailableSlots(serviceId, date);
        
        // Get user's existing appointments for this date
        List<Appointment> userAppointments = appointmentRepository.findByCustomer_User_IdAndDate(userId, date);
        
        // Filter to only PENDING and CONFIRMED
        List<Appointment> activeAppointments = userAppointments.stream()
            .filter(a -> a.getStatus() == AppointmentStatus.PENDING || 
                        a.getStatus() == AppointmentStatus.CONFIRMED)
            .collect(Collectors.toList());
        
        // Mark overlapping slots as disabled
        for (SlotDto slot : slots) {
            slot.setDisabled(isSlotOverlapWithAppointments(slot, activeAppointments));
        }
        
        return slots;
    }

    /**
     * Check if a slot overlaps with any of the user's existing appointments.
     */
    private boolean isSlotOverlapWithAppointments(SlotDto slot, List<Appointment> appointments) {
        LocalTime slotStart = slot.getStartTime();
        LocalTime slotEnd = slot.getEndTime();
        
        for (Appointment appt : appointments) {
            // Check if appointment times overlap with slot
            if (slotStart.isBefore(appt.getEndTime()) && slotEnd.isAfter(appt.getStartTime())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create appointment with slot reservation.
     * Supports both old format (slotId) and new format (selectedDate + selectedTime).
     * CRITICAL: Validates that selected time is in the future - this prevents booking of past slots
     * even if called via API directly.
     */
    @Transactional
    public AppointmentDto createAppointment(Long userId, CreateAppointmentRequest request) {
        try {
            validateCreateRequest(request);

            CustomerProfile customer = getOrCreateCustomerProfile(userId);

            Services service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));

            int slotsNeeded = calculateSlotsNeeded(service.getDurationMinutes());

            List<Slot> reservedSlots;
            LocalDateTime startDateTime = null;
            
            // Support both old format (slotId) and new format (selectedDate + selectedTime)
            if (request.isOldFormat()) {
            // Old format: use slotId
            Long slotId = request.getSlotId();
            Optional<Slot> slotOpt = getSlotById(slotId);
            
            if (slotOpt.isEmpty()) {
                throw new IllegalArgumentException("Khung giờ không tồn tại.");
            }
            
            startDateTime = slotOpt.get().getSlotTime();

            // VALIDATION: patient cannot book overlapping appointments
            enforceNoPatientOverlap(userId, startDateTime, slotsNeeded);
            
            // CRITICAL FIX: Validate slot time is in the future (applies to old format too)
            if (startDateTime.isBefore(LocalDateTime.now()) || startDateTime.equals(LocalDateTime.now())) {
                throw new IllegalArgumentException("Khung giờ này đã qua. Vui lòng chọn khung giờ khác.");
            }
            
            reservedSlots = reserveSlotById(slotId, slotsNeeded);
        } else {
            // New format: use selectedDate + selectedTime
            LocalDate selectedDate = request.getSelectedDate();
            LocalTime selectedTime = request.getSelectedTime();
            
            // CRITICAL FIX: Validate selectedDate is not in the past
            if (selectedDate.isBefore(LocalDate.now())) {
                throw new IllegalArgumentException("Không thể đặt lịch cho ngày trong quá khứ. Vui lòng chọn ngày trong tương lai.");
            }
            
            startDateTime = LocalDateTime.of(selectedDate, selectedTime);

            // VALIDATION: patient cannot book overlapping appointments
            enforceNoPatientOverlap(userId, startDateTime, slotsNeeded);

            // CRITICAL FIX: Validate both date and time are in the future
            if (startDateTime.isBefore(LocalDateTime.now()) || startDateTime.equals(LocalDateTime.now())) {
                throw new IllegalArgumentException("Không thể đặt lịch trong quá khứ. Vui lòng chọn thời gian trong tương lai.");
            }

            if (selectedTime.isBefore(LocalTime.of(8, 0)) || selectedTime.isAfter(LocalTime.of(16, 30))) {
                throw new IllegalArgumentException("Thời gian phải trong khoảng 08:00 - 17:00.");
            }

            reservedSlots = reserveSlots(startDateTime, slotsNeeded);
        }

        if (reservedSlots.isEmpty()) {
            throw new IllegalArgumentException("Khung giờ này đã đầy. Vui lòng chọn thời gian khác.");
        }

        Appointment appointment = createPendingAppointment(
            customer,
            service,
            reservedSlots,
            request.getContactChannel().trim().toUpperCase(),
            request.getContactValue().trim(),
            request.getPatientNote()
        );

        return toDto(appointment);
        } catch (BusinessException | IllegalArgumentException e) {
            // propagate known problems so controller can handle them
            throw e;
        } catch (Exception e) {
            // unexpected failure - log and convert to business exception
            logger.error("Unexpected error while creating appointment", e);
            throw new BusinessException("Đã xảy ra lỗi khi đặt lịch. Vui lòng thử lại.");
        }
    }

    /**
     * Confirm appointment after successful payment.
     */
    @Transactional
    public AppointmentDto confirmAppointment(Long appointmentId) {
        Appointment appointment = confirmAppointmentInternal(appointmentId);
        return toDto(appointment);
    }

    /**
     * Cancel appointment and release slots.
     */
    @Transactional
    public AppointmentDto cancelAppointment(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Lịch hẹn không tồn tại."));

        if (appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalArgumentException("Không thể hủy lịch hẹn đã hoàn thành.");
        }

        Appointment cancelled = cancelAppointmentInternal(appointmentId);
        return toDto(cancelled);
    }

    public List<AppointmentDto> getMyAppointments(Long userId) {
        List<Appointment> list = appointmentRepository.findByCustomer_User_IdOrderByDateDesc(userId);
        return list.stream().map(this::toDto).collect(Collectors.toList());
    }

    public Optional<AppointmentDto> getAppointmentDetail(Long userId, Long appointmentId) {
        return appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId)
                .map(this::toDto);
    }

    @Transactional
    public Optional<AppointmentDto> checkIn(Long userId, Long appointmentId) {
        Optional<Appointment> opt = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId);
        if (opt.isEmpty()) return Optional.empty();

        Appointment a = opt.get();
        LocalDate today = LocalDate.now();

        if (!a.getDate().equals(today) || a.getStatus() != AppointmentStatus.CONFIRMED) {
            return Optional.empty();
        }

        a.setStatus(AppointmentStatus.CHECKED_IN);
        appointmentRepository.save(a);
        return Optional.of(toDto(a));
    }

    private AppointmentDto toDto(Appointment a) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(a.getId());
        dto.setDate(a.getDate());
        dto.setStartTime(a.getStartTime());
        dto.setEndTime(a.getEndTime());
        dto.setStatus(a.getStatus().name());
        dto.setNotes(a.getNotes());
        dto.setContactChannel(a.getContactChannel());
        dto.setContactValue(a.getContactValue());

        if (a.getService() != null) {
            dto.setServiceId(a.getService().getId());
            dto.setServiceName(a.getService().getName());
        }

        if (a.getDentist() != null) {
            dto.setDentistId(a.getDentist().getId());
            dto.setDentistName(a.getDentist().getFullName());
        }

        dto.setCanCheckIn(a.getDate().equals(LocalDate.now()) && a.getStatus() == AppointmentStatus.CONFIRMED);

        return dto;
    }

    // ------------------------------------------------------------------
    // Internal helpers originally part of BookingService
    // ------------------------------------------------------------------

    /**
     * Internal entity-level retrieval; does not convert to DTO.
     */
    private List<Slot> fetchAllSlotsForDate(LocalDate date) {
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = LocalDate.now();
        LocalDateTime dayStart = LocalDateTime.of(date, CLINIC_OPEN_TIME);
        LocalDateTime dayEnd = LocalDateTime.of(date, CLINIC_CLOSE_TIME);

        // validation: no past dates
        if (date.isBefore(today)) {
            logger.warn("Requested slots for past date {}", date);
            return new ArrayList<>();
        }
        if (dayEnd.isBefore(now)) {
            return new ArrayList<>();
        }

        List<Slot> slots;
        if (date.equals(today)) {
            LocalDateTime currentSlotStart = findSlotStartTime(now);
            slots = slotRepository.findAllSlotsForToday(currentSlotStart, dayEnd);
        } else {
            slots = slotRepository.findAllSlotsBetweenTimes(dayStart, dayEnd);
        }
        return slots;
    }

    /**
     * Calculate number of 30‑minute slots needed for given duration.
     */
    public static int calculateSlotsNeeded(int durationMinutes) {
        return (int) Math.ceil((double) durationMinutes / SLOT_DURATION_MINUTES);
    }

    /**
     * Ensure that the given user does not already have a pending/confirmed
     * appointment overlapping the window starting at startDateTime and
     * lasting slotsNeeded slots.  Throws BusinessException if an overlap exists.
    /**
     * Ensure that the given user does not already have a pending/confirmed
     * appointment overlapping the window starting at startDateTime and
     * lasting slotsNeeded slots.  Throws BusinessException if an overlap exists.
     */
    private void enforceNoPatientOverlap(Long userId, LocalDateTime startDateTime, int slotsNeeded) {
        LocalDate date = startDateTime.toLocalDate();
        LocalTime startTime = startDateTime.toLocalTime();
        LocalTime endTime = startDateTime.plusMinutes((long) slotsNeeded * SLOT_DURATION_MINUTES).toLocalTime();

        // use native query with explicit time casting to avoid SQL Server type mismatch
        // convert enum names to strings for SQL Server native query
        logger.debug("Checking overlap for user={}, date={}, start={}, end={}",
                 userId, date, startTime, endTime);
        boolean exists = appointmentRepository
            .existsPatientOverlap(
                userId,
                date,
                List.of(
                    com.dentalclinic.model.appointment.AppointmentStatus.PENDING.name(),
                    com.dentalclinic.model.appointment.AppointmentStatus.CONFIRMED.name()
                ),
                endTime,
                startTime);
        logger.debug("Overlap exists? {}", exists);
        if (exists) {
            throw new com.dentalclinic.exception.BusinessException("Bạn đã có lịch khám trùng thời gian này");
        }
    }

    private LocalDateTime findSlotStartTime(LocalDateTime now) {
        LocalTime time = now.toLocalTime();
        int minute = time.getMinute();
        int slotMinute = (minute / 30) * 30;
        LocalDateTime slotStart = LocalDateTime.of(now.toLocalDate(), LocalTime.of(time.getHour(), slotMinute));
        if (now.isAfter(slotStart)) {
            slotStart = slotStart.plusMinutes(30);
        }
        return slotStart;
    }

    @Transactional
    public List<Slot> reserveSlots(LocalDateTime startDateTime, int slotsNeeded) {
        LocalDateTime now = LocalDateTime.now();
        if (startDateTime.isBefore(now) || startDateTime.equals(now)) {
            throw new IllegalArgumentException("Không thể đặt lịch trong quá khứ. Vui lòng chọn thời gian trong tương lai.");
        }
        List<Slot> reservedSlots = new ArrayList<>();
        for (int i = 0; i < slotsNeeded; i++) {
            LocalDateTime slotTime = startDateTime.plusMinutes((long) i * SLOT_DURATION_MINUTES);
            if (slotTime.isBefore(now) || slotTime.equals(now)) {
                rollbackReservedSlots(reservedSlots);
                throw new IllegalArgumentException("Khung giờ đã qua. Vui lòng chọn thời gian khác.");
            }
            int updatedRows = slotRepository.incrementBookedCountIfAvailable(slotTime);
            if (updatedRows == 0) {
                rollbackReservedSlots(reservedSlots);
                return new ArrayList<>();
            }
            Optional<Slot> slotOpt = slotRepository.findBySlotTimeAndActiveTrue(slotTime);
            if (slotOpt.isPresent()) {
                reservedSlots.add(slotOpt.get());
            } else {
                rollbackReservedSlots(reservedSlots);
                return new ArrayList<>();
            }
        }
        return reservedSlots;
    }

    private void rollbackReservedSlots(List<Slot> reservedSlots) {
        for (Slot slot : reservedSlots) {
            try {
                slotRepository.decrementBookedCount(slot.getSlotTime());
            } catch (Exception e) {
                logger.error("Failed to rollback slot at {}: {}", slot.getSlotTime(), e.getMessage());
            }
        }
    }

    @Transactional
    public Optional<Slot> getSlotById(Long slotId) {
        return slotRepository.findById(slotId);
    }

    @Transactional
    public List<Slot> reserveSlotById(Long slotId, int slotsNeeded) {
        Optional<Slot> slotOpt = slotRepository.findById(slotId);
        if (slotOpt.isEmpty()) {
            return new ArrayList<>();
        }
        return reserveSlots(slotOpt.get().getSlotTime(), slotsNeeded);
    }

    @Transactional
    public void releaseSlots(List<Slot> slots) {
        for (Slot slot : slots) {
            try {
                slotRepository.decrementBookedCount(slot.getSlotTime());
            } catch (Exception e) {
                logger.error("Failed to release slot at {}: {}", slot.getSlotTime(), e.getMessage());
            }
        }
    }

    /**
     * Appointment lifecycle helpers moved from BookingService.
     */
    @Transactional
    public Appointment createPendingAppointment(
            CustomerProfile customer,
            Services service,
            List<Slot> reservedSlots,
            String channel,
            String value,
            String notes) {
        Appointment a = new Appointment();
        a.setCustomer(customer);
        a.setService(service);
        a.setDate(reservedSlots.get(0).getSlotTime().toLocalDate());
        a.setStartTime(reservedSlots.get(0).getSlotTime().toLocalTime());
        a.setEndTime(reservedSlots.get(reservedSlots.size() - 1).getSlotTime()
                .toLocalTime().plusMinutes(SLOT_DURATION_MINUTES));
        a.setContactChannel(channel);
        a.setContactValue(value);
        a.setNotes(notes);
        a.setStatus(AppointmentStatus.PENDING);

        // convert each Slot into an AppointmentSlot and attach
        a.clearAppointmentSlots(); // just in case
        for (int i = 0; i < reservedSlots.size(); i++) {
            AppointmentSlot as = new AppointmentSlot(a, reservedSlots.get(i), i);
            a.addAppointmentSlot(as);
        }

        return appointmentRepository.save(a);
    }

    @Transactional
    public Appointment confirmAppointmentInternal(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment cancelAppointmentInternal(Long appointmentId) {
        Optional<Appointment> opt = appointmentRepository.findByIdWithSlots(appointmentId);
        if (opt.isEmpty()) {
            throw new IllegalArgumentException("Appointment not found");
        }
        Appointment a = opt.get();
        // release booked slots via appointmentSlots relationship
        List<Slot> slotsToRelease = new ArrayList<>();
        for (AppointmentSlot as : a.getAppointmentSlots()) {
            if (as.getSlot() != null) {
                slotsToRelease.add(as.getSlot());
            }
        }
        releaseSlots(slotsToRelease);
        a.setStatus(AppointmentStatus.CANCELLED);
        return appointmentRepository.save(a);
    }

    public boolean checkDentistOverlap(Long dentistProfileId, LocalDate date,
                                       LocalTime startTime, LocalTime endTime, Long excludeAppointmentId) {
        if (excludeAppointmentId == null) {
            return appointmentRepository.hasOverlappingAppointment(dentistProfileId, date, startTime, endTime);
        } else {
            return appointmentRepository.hasOverlappingAppointmentExcludingSelf(
                    dentistProfileId, date, startTime, endTime, excludeAppointmentId);
        }
    }

    public Optional<Appointment> getAppointmentWithDetails(Long appointmentId) {
        return appointmentRepository.findByIdWithDetails(appointmentId);
    }

    @Transactional
    public void initializeSlotsForDate(LocalDate date, int capacity) {
        LocalDateTime current = LocalDateTime.of(date, CLINIC_OPEN_TIME);
        LocalDateTime end = LocalDateTime.of(date, CLINIC_CLOSE_TIME);
        int slotsCreated = 0;
        while (current.isBefore(end)) {
            Optional<Slot> existing = slotRepository.findBySlotTimeAndActiveTrue(current);
            if (existing.isEmpty()) {
                Slot slot = new Slot(current, capacity);
                slotRepository.save(slot);
                slotsCreated++;
            }
            current = current.plusMinutes(SLOT_DURATION_MINUTES);
        }
        logger.info("Initialized {} slots for date {} with capacity {}", slotsCreated, date, capacity);
    }

    @Transactional
    public void updateCapacityForDate(LocalDate date, int newCapacity) {
        List<Slot> slots = fetchAllSlotsForDate(date);
        for (Slot slot : slots) {
            if (slot.getBookedCount() > newCapacity) {
                throw new IllegalStateException(
                    "Cannot reduce capacity to " + newCapacity + 
                    " as slot at " + slot.getSlotTime() + " already has " + 
                    slot.getBookedCount() + " bookings");
            }
            slot.setCapacity(newCapacity);
            slotRepository.save(slot);
        }
        logger.info("Updated capacity to {} for all slots on {}", newCapacity, date);
    }
}
