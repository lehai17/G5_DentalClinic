package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.RescheduleAppointmentRequest;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.exception.BookingErrorCode;
import com.dentalclinic.exception.BookingException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentSlot;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.SlotRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.notification.NotificationService;
import com.dentalclinic.service.wallet.WalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CustomerAppointmentService {
    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);
    private static final int SLOT_MIN = 30;
    private static final int CANCEL_MIN_HOURS_BEFORE = 24;
    private static final double DEFAULT_DEPOSIT_RATE = 0d;

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern VN_PHONE_PATTERN = Pattern.compile("^(0|\\+84)\\d{9,10}$");
    private static final List<String> ACTIVE_BOOKING_STATUSES = List.of(
            AppointmentStatus.PENDING.name(),
            AppointmentStatus.PENDING_DEPOSIT.name(),
            AppointmentStatus.CONFIRMED.name(),
            AppointmentStatus.CHECKED_IN.name()
    );

    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final NotificationService notificationService;
    private final WalletService walletService;

    public CustomerAppointmentService(CustomerProfileRepository customerProfileRepository,
                                      UserRepository userRepository,
                                      AppointmentRepository appointmentRepository,
                                      ServiceRepository serviceRepository,
                                      SlotRepository slotRepository,
                                      NotificationService notificationService,
                                      WalletService walletService) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
        this.notificationService = notificationService;
        this.walletService = walletService;
    }

    public static int calculateSlotsNeeded(int durationMinutes) {
        return (int) Math.ceil((double) durationMinutes / SLOT_MIN);
    }

    public List<SlotDto> getAvailableSlots(Long userId, Long serviceId, LocalDate date) {
        validateBookingUser(userId);
        Services service = validateService(serviceId);
        validateSelectedDate(date);

        int slotsNeeded = calculateSlotsNeeded(service.getDurationMinutes());
        List<Slot> slots = getAvailableSlotsForService(date, service.getDurationMinutes());
        return slots.stream().map(s -> {
            SlotDto dto = toSlotDto(s);
            LocalDateTime end = s.getSlotTime().plusMinutes((long) slotsNeeded * SLOT_MIN);
            dto.setDisabled(hasCustomerOverlap(userId, s.getSlotTime().toLocalDate(), s.getSlotTime().toLocalTime(), end.toLocalTime(), null));
            return dto;
        }).collect(Collectors.toList());
    }

    public List<SlotDto> getAllSlotsForDate(LocalDate date) {
        validateSelectedDate(date);
        return getAllSlotsEntitiesForDate(date).stream().map(this::toSlotDto).collect(Collectors.toList());
    }

    @Transactional
    public AppointmentDto createAppointment(Long userId, CreateAppointmentRequest request) {
        validateCreateRequest(request);
        User user = validateBookingUser(userId);
        CustomerProfile customer = getOrCreateCustomerProfile(user);
        Services service = validateService(request.getServiceId());
        validateClientDeposit(request.getDepositAmount(), service);

        int slotsNeeded = calculateSlotsNeeded(service.getDurationMinutes());
        LocalDateTime start = resolveStartDateTime(request);
        LocalDateTime end = start.plusMinutes((long) slotsNeeded * SLOT_MIN);
        validateNotInPast(start);
        validateWorkingWindow(start, end);
        validateNoCustomerOverlap(user.getId(), start.toLocalDate(), start.toLocalTime(), end.toLocalTime(), null);

        List<Slot> reserved = reserveSlots(start, slotsNeeded);
        if (reserved.isEmpty()) throw new BookingException(BookingErrorCode.SLOT_FULL, "Slot Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ hÃƒÂ¡Ã‚ÂºÃ‚Â¿t chÃƒÂ¡Ã‚Â»Ã¢â‚¬â€.");
        Appointment created = createPendingAppointment(customer, service, reserved,
                request.getContactChannel().trim().toUpperCase(), request.getContactValue().trim(), request.getPatientNote());
        return toDto(created);
    }

    @Transactional
    public AppointmentDto rescheduleAppointment(Long userId, Long appointmentId, RescheduleAppointmentRequest request) {
        if (request == null || request.getSelectedDate() == null || request.getSelectedTime() == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "NgÃƒÆ’Ã‚Â y vÃƒÆ’Ã‚Â  giÃƒÂ¡Ã‚Â»Ã‚Â Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢i lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch lÃƒÆ’Ã‚Â  bÃƒÂ¡Ã‚ÂºÃ‚Â¯t buÃƒÂ¡Ã‚Â»Ã¢â€žÂ¢c.");
        }
        Appointment a = appointmentRepository.findByIdWithSlotsAndCustomerUserId(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "LÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch hÃƒÂ¡Ã‚ÂºÃ‚Â¹n khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i."));
        ensureRescheduleAllowed(a);

        Services service = validateService(a.getService() != null ? a.getService().getId() : null);
        int slotsNeeded = calculateSlotsNeeded(service.getDurationMinutes());
        LocalDateTime newStart = LocalDateTime.of(request.getSelectedDate(), request.getSelectedTime());
        LocalDateTime newEnd = newStart.plusMinutes((long) slotsNeeded * SLOT_MIN);
        validateNotInPast(newStart);
        validateWorkingWindow(newStart, newEnd);
        validateNoCustomerOverlap(userId, newStart.toLocalDate(), newStart.toLocalTime(), newEnd.toLocalTime(), a.getId());

        List<Slot> oldSlots = a.getAppointmentSlots().stream().map(AppointmentSlot::getSlot).collect(Collectors.toCollection(ArrayList::new));
        List<Slot> newSlots = reserveSlots(newStart, slotsNeeded);
        if (newSlots.isEmpty()) throw new BookingException(BookingErrorCode.SLOT_FULL, "Slot Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ hÃƒÂ¡Ã‚ÂºÃ‚Â¿t chÃƒÂ¡Ã‚Â»Ã¢â‚¬â€.");

        a.setDate(newStart.toLocalDate());
        a.setStartTime(newStart.toLocalTime());
        a.setEndTime(newEnd.toLocalTime());
        a.clearAppointmentSlots();
        for (int i = 0; i < newSlots.size(); i++) a.addAppointmentSlot(new AppointmentSlot(a, newSlots.get(i), i));
        Appointment saved = appointmentRepository.save(a);
        releaseSlots(oldSlots);
        notificationService.notifyBookingUpdated(saved, "Thay Ã„â€˜Ã¡Â»â€¢i thÃ¡Â»Âi gian khÃƒÂ¡m");
        return toDto(saved);
    }

    @Transactional
    public AppointmentDto confirmAppointment(Long appointmentId) { return toDto(confirmAppointmentEntity(appointmentId)); }

    @Transactional
    public AppointmentDto cancelAppointment(Long userId, Long appointmentId) {
        Appointment a = appointmentRepository.findByIdWithSlotsAndCustomerUserId(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lich hen khong ton tai."));
        ensureCancelAllowed(a);

        List<Slot> toRelease = a.getAppointmentSlots().stream()
                .map(AppointmentSlot::getSlot)
                .collect(Collectors.toCollection(ArrayList::new));

        a.setStatus(AppointmentStatus.CANCELLED);
        Appointment savedAppt = appointmentRepository.save(a);
        releaseSlots(toRelease);
        return toDto(savedAppt);
    }

    @Transactional
    public Appointment cancelAppointmentByStaff(Long appointmentId, String reason) {
        Appointment cancelled = cancelAppointmentEntity(appointmentId, reason, true);
        notificationService.notifyBookingCancelled(cancelled, reason);
        return cancelled;
    }

    @Transactional
    public Appointment cancelUnpaidAppointment(Long appointmentId, String reason) {
        System.out.println("=== cancelUnpaidAppointment (delete) called with appointmentId: " + appointmentId);
        Appointment a = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lich hen khong ton tai."));
        
        if (a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Khong the huy lich voi trang thai hien tai.");
        }
        
        List<Slot> toRelease = a.getAppointmentSlots().stream().map(AppointmentSlot::getSlot).toList();
        releaseSlots(toRelease);
        appointmentRepository.delete(a);
        return a;
    }

    @Transactional
    public Appointment markDepositPaymentSuccess(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "LÃ¡Â»â€¹ch hÃ¡ÂºÂ¹n khÃƒÂ´ng tÃ¡Â»â€œn tÃ¡ÂºÂ¡i."));
        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "KhÃƒÂ´ng thÃ¡Â»Æ’ xÃƒÂ¡c nhÃ¡ÂºÂ­n thanh toÃƒÂ¡n cho lÃ¡Â»â€¹ch hÃ¡ÂºÂ¹n nÃƒÂ y.");
        }
        appointment.setStatus(AppointmentStatus.PENDING);
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.notifyBookingCreated(saved);
        return saved;
    }

    public List<AppointmentDto> getMyAppointments(Long userId) {
        return appointmentRepository.findByCustomer_User_IdOrderByDateDesc(userId).stream()
                .filter(a -> a.getStatus() != AppointmentStatus.PENDING_DEPOSIT)
                .map(this::toDto).collect(Collectors.toList());
    }

    public Page<AppointmentDto> getMyAppointmentsPage(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("date"), Sort.Order.desc("startTime")));
        Page<Appointment> appointments = appointmentRepository.findByCustomer_User_Id(userId, pageable);
        List<AppointmentDto> content = appointments.getContent().stream()
                .filter(a -> a.getStatus() != AppointmentStatus.PENDING_DEPOSIT)
                .map(this::toDto).toList();
        return new PageImpl<>(content, pageable, appointments.getTotalElements());
    }

    public Optional<AppointmentDto> getAppointmentDetail(Long userId, Long appointmentId) {
        return appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId).map(this::toDto);
    }

    @Transactional
    public Optional<AppointmentDto> checkIn(Long userId, Long appointmentId) {
        Optional<Appointment> opt = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId);
        if (opt.isEmpty()) return Optional.empty();
        Appointment a = opt.get();
        if (!a.getDate().equals(nowDateTime().toLocalDate()) || a.getStatus() != AppointmentStatus.CONFIRMED) return Optional.empty();
        a.setStatus(AppointmentStatus.CHECKED_IN);
        return Optional.of(toDto(appointmentRepository.save(a)));
    }

    public boolean checkDentistOverlap(Long dentistProfileId, LocalDate date, LocalTime startTime, LocalTime endTime, Long excludeAppointmentId) {
        return excludeAppointmentId == null
                ? appointmentRepository.hasOverlappingAppointment(dentistProfileId, date, startTime, endTime)
                : appointmentRepository.hasOverlappingAppointmentExcludingSelf(dentistProfileId, date, startTime, endTime, excludeAppointmentId);
    }

    private List<Slot> getAvailableSlotsForService(LocalDate date, int durationMinutes) {
        int slotsNeeded = calculateSlotsNeeded(durationMinutes);
        LocalDateTime now = nowDateTime();
        LocalDateTime dayStart = LocalDateTime.of(date, OPEN);
        LocalDateTime dayEnd = LocalDateTime.of(date, CLOSE);
        if (date.isBefore(now.toLocalDate()) || dayEnd.isBefore(now)) return new ArrayList<>();

        List<Slot> all = date.equals(now.toLocalDate())
                ? slotRepository.findAvailableSlotsForToday(findNextSlotStart(now), dayEnd)
                : slotRepository.findAvailableSlotsBetweenTimes(dayStart, dayEnd);

        List<Slot> ok = new ArrayList<>();
        for (int i = 0; i <= all.size() - slotsNeeded; i++) {
            boolean good = true;
            for (int j = 0; j < slotsNeeded; j++) {
                Slot s = all.get(i + j);
                if (!s.getSlotTime().equals(all.get(i).getSlotTime().plusMinutes((long) j * SLOT_MIN)) || !s.isAvailable()) { good = false; break; }
            }
            if (good) {
                LocalDateTime st = all.get(i).getSlotTime();
                if (isInsideWorkingWindow(st, st.plusMinutes((long) slotsNeeded * SLOT_MIN))) ok.add(all.get(i));
            }
        }
        return ok;
    }

    private List<Slot> getAllSlotsEntitiesForDate(LocalDate date) {
        LocalDateTime now = nowDateTime();
        LocalDateTime dayStart = LocalDateTime.of(date, OPEN);
        LocalDateTime dayEnd = LocalDateTime.of(date, CLOSE);
        if (date.isBefore(now.toLocalDate()) || dayEnd.isBefore(now)) return new ArrayList<>();

        List<Slot> list = date.equals(now.toLocalDate())
                ? slotRepository.findAllSlotsForToday(findNextSlotStart(now), dayEnd)
                : slotRepository.findAllSlotsBetweenTimes(dayStart, dayEnd);
        return list.stream().filter(s -> isInsideWorkingWindow(s.getSlotTime(), s.getSlotTime().plusMinutes(SLOT_MIN))).toList();
    }

    @Transactional
    private List<Slot> reserveSlots(LocalDateTime startDateTime, int slotsNeeded) {
        if (!startDateTime.isAfter(nowDateTime())) throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â·t lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch trong quÃƒÆ’Ã‚Â¡ khÃƒÂ¡Ã‚Â»Ã‚Â©.");
        if (slotsNeeded <= 0) throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "ThÃƒÂ¡Ã‚Â»Ã‚Âi lÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£ng Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â·t lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        LocalDateTime endDateTime = startDateTime.plusMinutes((long) slotsNeeded * SLOT_MIN);
        if (!isInsideWorkingWindow(startDateTime, endDateTime)) throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "ThÃƒÂ¡Ã‚Â»Ã‚Âi gian nÃƒÂ¡Ã‚ÂºÃ‚Â±m ngoÃƒÆ’Ã‚Â i giÃƒÂ¡Ã‚Â»Ã‚Â lÃƒÆ’Ã‚Â m viÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡c.");

        List<Slot> locked = slotRepository.findActiveSlotsForUpdate(startDateTime, endDateTime);
        if (locked.size() != slotsNeeded) return new ArrayList<>();
        for (int i = 0; i < locked.size(); i++) {
            if (!locked.get(i).getSlotTime().equals(startDateTime.plusMinutes((long) i * SLOT_MIN)) || !locked.get(i).isAvailable()) return new ArrayList<>();
        }
        for (Slot s : locked) { s.setBookedCount(s.getBookedCount() + 1); slotRepository.save(s); }
        return locked;
    }

    @Transactional
    private void releaseSlots(List<Slot> slots) {
        for (Slot s : slots) {
            Slot locked = slotRepository.findBySlotTimeAndActiveTrueForUpdate(s.getSlotTime())
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giÃƒÂ¡Ã‚Â»Ã‚Â khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i."));
            if (locked.getBookedCount() <= 0) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "DÃƒÂ¡Ã‚Â»Ã‚Â¯ liÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡u slot khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
            locked.setBookedCount(locked.getBookedCount() - 1);
            slotRepository.save(locked);
        }
    }

    @Transactional
    private Appointment createPendingAppointment(CustomerProfile customer, Services service, List<Slot> reservedSlots,
                                                 String contactChannel, String contactValue, String notes) {
        if (reservedSlots.isEmpty()) throw new BookingException(BookingErrorCode.SLOT_FULL, "KhÃƒÆ’Ã‚Â´ng cÃƒÆ’Ã‚Â³ slot Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c giÃƒÂ¡Ã‚Â»Ã‚Â¯.");
        Slot first = reservedSlots.get(0), last = reservedSlots.get(reservedSlots.size() - 1);
        Appointment a = new Appointment();
        a.setCustomer(customer); a.setService(service);
        a.setDate(first.getSlotTime().toLocalDate()); a.setStartTime(first.getStartTime()); a.setEndTime(last.getEndTime());
        // SQL Server CHECK constraint on appointment.status does not allow PENDING_DEPOSIT.
        // Keep booking in PENDING state after creating/deposit flow.
        a.setStatus(AppointmentStatus.PENDING); a.setContactChannel(contactChannel); a.setContactValue(contactValue);
        a.setNotes(notes != null ? notes.trim() : null);
        for (int i = 0; i < reservedSlots.size(); i++) a.addAppointmentSlot(new AppointmentSlot(a, reservedSlots.get(i), i));
        return appointmentRepository.save(a);
    }

    @Transactional
    private Appointment confirmAppointmentEntity(Long appointmentId) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "LÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch hÃƒÂ¡Ã‚ÂºÃ‚Â¹n khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i."));
        if (a.getStatus() != AppointmentStatus.PENDING && a.getStatus() != AppointmentStatus.PENDING_DEPOSIT) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ xÃƒÆ’Ã‚Â¡c nhÃƒÂ¡Ã‚ÂºÃ‚Â­n lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch hÃƒÂ¡Ã‚ÂºÃ‚Â¹n ÃƒÂ¡Ã‚Â»Ã…Â¸ trÃƒÂ¡Ã‚ÂºÃ‚Â¡ng thÃƒÆ’Ã‚Â¡i hiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
        }
        a.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(a);
    }

    @Transactional
    private Appointment cancelAppointmentEntity(Long appointmentId, String reason, boolean refundIfEligible) {
        System.out.println("=== cancelAppointmentEntity called with appointmentId: " + appointmentId);
        Appointment a = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lich hen khong ton tai."));
        System.out.println("Appointment found, status: " + a.getStatus() + ", service: " + a.getService());
        
        if (a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Khong the huy lich voi trang thai hien tai.");
        }
        
        // HoÃƒÂ n tiÃ¡Â»Ân nÃ¡ÂºÂ¿u Ã„â€˜ÃƒÂ£ thanh toÃƒÂ¡n cÃ¡Â»Âc (status = PENDING hoÃ¡ÂºÂ·c CONFIRMED)
        if (refundIfEligible && (a.getStatus() == AppointmentStatus.PENDING || a.getStatus() == AppointmentStatus.CONFIRMED)) {
            try {
                if (a.getService() == null) {
                    System.out.println("LOG: Appointment " + a.getId() + " has no service. Skipping refund.");
                } else {
                    System.out.println("Processing refund for appointment " + a.getId() + ", service: " + a.getService().getId());
                    BigDecimal servicePrice = BigDecimal.valueOf(a.getService().getPrice());
                    // GiÃ¡ÂºÂ£ Ã„â€˜Ã¡Â»â€¹nh hoÃƒÂ n 50% tiÃ¡Â»Ân cÃ¡Â»Âc (tÃƒÂ­nh theo giÃƒÂ¡ dÃ¡Â»â€¹ch vÃ¡Â»Â¥)
                    BigDecimal refundAmount = servicePrice.multiply(BigDecimal.valueOf(0.5));
                    
                    if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                        walletService.refund(
                            a.getCustomer(),
                            refundAmount,
                            "Hoan tien dat coc lich hen #" + a.getId(),
                            a.getId()
                        );
                        System.out.println("Refund of " + refundAmount + " processed successfully");
                    }
                }
            } catch (Exception e) {
                System.err.println("ERROR processing refund: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        List<Slot> toRelease = a.getAppointmentSlots().stream().map(AppointmentSlot::getSlot).toList();
        a.setStatus(AppointmentStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) a.setNotes(reason.trim());
        Appointment saved = appointmentRepository.save(a);
        releaseSlots(toRelease);
        return saved;
    }

    private SlotDto toSlotDto(Slot slot) {
        SlotDto dto = new SlotDto();
        dto.setId(slot.getId()); dto.setDate(slot.getSlotTime().toLocalDate());
        dto.setStartTime(slot.getSlotTime().toLocalTime()); dto.setEndTime(slot.getSlotTime().toLocalTime().plusMinutes(SLOT_MIN));
        dto.setAvailable(slot.isAvailable()); dto.setCapacity(slot.getCapacity()); dto.setBookedCount(slot.getBookedCount()); dto.setAvailableSpots(slot.getAvailableSpots());
        return dto;
    }

    private void validateCreateRequest(CreateAppointmentRequest request) {
        if (request == null) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "DÃƒÂ¡Ã‚Â»Ã‚Â¯ liÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡u Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â·t lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        if (request.getServiceId() == null || request.getServiceId() <= 0) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lÃƒÆ’Ã‚Â²ng chÃƒÂ¡Ã‚Â»Ã‚Ân dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã‚Â¥ hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        if (!request.isOldFormat() && !request.isNewFormat()) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lÃƒÆ’Ã‚Â²ng chÃƒÂ¡Ã‚Â»Ã‚Ân ngÃƒÆ’Ã‚Â y vÃƒÆ’Ã‚Â  giÃƒÂ¡Ã‚Â»Ã‚Â hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        if (request.getPatientNote() != null && request.getPatientNote().length() > 500) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ghi chÃƒÆ’Ã‚Âº tÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœi Ãƒâ€žÃ¢â‚¬Ëœa 500 kÃƒÆ’Ã‚Â½ tÃƒÂ¡Ã‚Â»Ã‚Â±.");
        if (request.getContactChannel() == null || request.getContactChannel().isBlank()) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lÃƒÆ’Ã‚Â²ng chÃƒÂ¡Ã‚Â»Ã‚Ân kÃƒÆ’Ã‚Âªnh liÃƒÆ’Ã‚Âªn hÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        if (request.getContactValue() == null || request.getContactValue().isBlank()) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lÃƒÆ’Ã‚Â²ng nhÃƒÂ¡Ã‚ÂºÃ‚Â­p thÃƒÆ’Ã‚Â´ng tin liÃƒÆ’Ã‚Âªn hÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");

        String channel = request.getContactChannel().trim().toUpperCase();
        String value = request.getContactValue().trim();
        if (("PHONE".equals(channel) || "ZALO".equals(channel)) && !VN_PHONE_PATTERN.matcher(value.replaceAll("\\s+", "")).matches())
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "SÃƒÂ¡Ã‚Â»Ã¢â‚¬Ëœ Ãƒâ€žÃ¢â‚¬ËœiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n thoÃƒÂ¡Ã‚ÂºÃ‚Â¡i/Zalo khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        if ("EMAIL".equals(channel) && !EMAIL_PATTERN.matcher(value).matches())
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Email khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        if (!List.of("PHONE", "ZALO", "EMAIL").contains(channel))
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "KÃƒÆ’Ã‚Âªnh liÃƒÆ’Ã‚Âªn hÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡ chÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c: PHONE, ZALO, EMAIL.");
        if (request.getDepositAmount() != null && request.getDepositAmount() < 0)
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "TiÃƒÂ¡Ã‚Â»Ã‚Ân cÃƒÂ¡Ã‚Â»Ã‚Âc khÃƒÆ’Ã‚Â´ng Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c ÃƒÆ’Ã‚Â¢m.");
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase();
            if (!"PENDING".equals(status) && !"CONFIRMED".equals(status))
                throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c khÃƒÂ¡Ã‚Â»Ã…Â¸i tÃƒÂ¡Ã‚ÂºÃ‚Â¡o vÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi trÃƒÂ¡Ã‚ÂºÃ‚Â¡ng thÃƒÆ’Ã‚Â¡i PENDING hoÃƒÂ¡Ã‚ÂºÃ‚Â·c CONFIRMED.");
        }
    }

    private User validateBookingUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "NgÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Âi dÃƒÆ’Ã‚Â¹ng khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i."));
        if (user.getRole() != Role.CUSTOMER) throw new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "ChÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡nh nhÃƒÆ’Ã‚Â¢n mÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi Ãƒâ€žÃ¢â‚¬ËœÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£c Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â·t lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch.");
        if (user.getStatus() != UserStatus.ACTIVE) throw new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "TÃƒÆ’Ã‚Â i khoÃƒÂ¡Ã‚ÂºÃ‚Â£n Ãƒâ€žÃ¢â‚¬Ëœang bÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ khÃƒÆ’Ã‚Â³a.");
        return user;
    }

    private CustomerProfile getOrCreateCustomerProfile(User user) {
        return customerProfileRepository.findByUser_Id(user.getId()).orElseGet(() -> {
            CustomerProfile p = new CustomerProfile();
            p.setUser(user); p.setFullName(user.getEmail() != null ? user.getEmail() : "KhÃƒÆ’Ã‚Â¡ch hÃƒÆ’Ã‚Â ng");
            return customerProfileRepository.save(p);
        });
    }

    private Services validateService(Long serviceId) {
        if (serviceId == null || serviceId <= 0) throw new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "DÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã‚Â¥ khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
        Services service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "DÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã‚Â¥ khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i."));
        if (!service.isActive()) throw new BookingException(BookingErrorCode.SERVICE_INACTIVE, "DÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã‚Â¥ Ãƒâ€žÃ¢â‚¬Ëœang tÃƒÂ¡Ã‚ÂºÃ‚Â¡m ngÃƒâ€ Ã‚Â°ng.");
        if (service.getDurationMinutes() <= 0) throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "ThÃƒÂ¡Ã‚Â»Ã‚Âi lÃƒâ€ Ã‚Â°ÃƒÂ¡Ã‚Â»Ã‚Â£ng dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã‚Â¥ khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        return service;
    }

    private void validateClientDeposit(Double clientDeposit, Services service) {
        if (clientDeposit == null) return;
        double expected = Math.max(0d, service.getPrice() * DEFAULT_DEPOSIT_RATE);
        if (Math.abs(clientDeposit - expected) > 0.0001d)
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "TiÃƒÂ¡Ã‚Â»Ã‚Ân cÃƒÂ¡Ã‚Â»Ã‚Âc khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡ theo cÃƒÂ¡Ã‚ÂºÃ‚Â¥u hÃƒÆ’Ã‚Â¬nh dÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã‚Â¥.");
    }

    private LocalDateTime resolveStartDateTime(CreateAppointmentRequest request) {
        if (request.isOldFormat()) {
            Slot slot = slotRepository.findById(request.getSlotId())
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giÃƒÂ¡Ã‚Â»Ã‚Â khÃƒÆ’Ã‚Â´ng tÃƒÂ¡Ã‚Â»Ã¢â‚¬Å“n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i."));
            return slot.getSlotTime();
        }
        return LocalDateTime.of(request.getSelectedDate(), request.getSelectedTime());
    }

    private void validateSelectedDate(LocalDate date) {
        if (date == null) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "NgÃƒÆ’Ã‚Â y khÃƒÆ’Ã‚Â´ng hÃƒÂ¡Ã‚Â»Ã‚Â£p lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡.");
        if (date.isBefore(nowDateTime().toLocalDate())) throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â·t lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch trong quÃƒÆ’Ã‚Â¡ khÃƒÂ¡Ã‚Â»Ã‚Â©.");
    }

    private void validateNotInPast(LocalDateTime startDateTime) {
        if (!startDateTime.isAfter(nowDateTime())) throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚ÂºÃ‚Â·t lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch trong quÃƒÆ’Ã‚Â¡ khÃƒÂ¡Ã‚Â»Ã‚Â©.");
    }

    private void validateWorkingWindow(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        LocalTime start = startDateTime.toLocalTime(), end = endDateTime.toLocalTime();
        if (endDateTime.toLocalDate().isAfter(startDateTime.toLocalDate()))
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "ThÃƒÂ¡Ã‚Â»Ã‚Âi gian nÃƒÂ¡Ã‚ÂºÃ‚Â±m ngoÃƒÆ’Ã‚Â i giÃƒÂ¡Ã‚Â»Ã‚Â lÃƒÆ’Ã‚Â m viÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡c.");
        if (start.isBefore(OPEN) || !end.isAfter(start) || end.isAfter(CLOSE) || start.equals(CLOSE))
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "ThÃƒÂ¡Ã‚Â»Ã‚Âi gian nÃƒÂ¡Ã‚ÂºÃ‚Â±m ngoÃƒÆ’Ã‚Â i giÃƒÂ¡Ã‚Â»Ã‚Â lÃƒÆ’Ã‚Â m viÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡c.");
        if (start.isBefore(LUNCH_END) && end.isAfter(LUNCH_START))
            throw new BookingException(BookingErrorCode.LUNCH_BREAK_CONFLICT, "Khung giÃƒÂ¡Ã‚Â»Ã‚Â cÃƒÂ¡Ã‚ÂºÃ‚Â¯t ngang thÃƒÂ¡Ã‚Â»Ã‚Âi gian nghÃƒÂ¡Ã‚Â»Ã¢â‚¬Â° trÃƒâ€ Ã‚Â°a 12:00-13:00.");
    }

    private void validateNoCustomerOverlap(Long userId, LocalDate date, LocalTime startTime, LocalTime endTime, Long excludeAppointmentId) {
        if (hasCustomerOverlap(userId, date, startTime, endTime, excludeAppointmentId))
            throw new BookingException(BookingErrorCode.BOOKING_CONFLICT, "BÃƒÂ¡Ã‚ÂºÃ‚Â¡n Ãƒâ€žÃ¢â‚¬ËœÃƒÆ’Ã‚Â£ cÃƒÆ’Ã‚Â³ lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch hÃƒÂ¡Ã‚ÂºÃ‚Â¹n trÃƒÆ’Ã‚Â¹ng thÃƒÂ¡Ã‚Â»Ã‚Âi Ãƒâ€žÃ¢â‚¬ËœiÃƒÂ¡Ã‚Â»Ã†â€™m nÃƒÆ’Ã‚Â y.");
    }

    private boolean hasCustomerOverlap(Long userId, LocalDate date, LocalTime startTime, LocalTime endTime, Long excludeAppointmentId) {
        return excludeAppointmentId == null
                ? appointmentRepository.existsCustomerOverlap(userId, date, startTime, endTime, ACTIVE_BOOKING_STATUSES)
                : appointmentRepository.existsCustomerOverlapExcludingAppointment(userId, excludeAppointmentId, date, startTime, endTime, ACTIVE_BOOKING_STATUSES);
    }

    private void ensureRescheduleAllowed(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED)
            throw new BookingException(BookingErrorCode.RESCHEDULE_NOT_ALLOWED, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢i lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch vÃƒÂ¡Ã‚Â»Ã¢â‚¬Âºi trÃƒÂ¡Ã‚ÂºÃ‚Â¡ng thÃƒÆ’Ã‚Â¡i hiÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¡n tÃƒÂ¡Ã‚ÂºÃ‚Â¡i.");
        if (!LocalDateTime.of(appointment.getDate(), appointment.getStartTime()).isAfter(nowDateTime()))
            throw new BookingException(BookingErrorCode.RESCHEDULE_NOT_ALLOWED, "KhÃƒÆ’Ã‚Â´ng thÃƒÂ¡Ã‚Â»Ã†â€™ Ãƒâ€žÃ¢â‚¬ËœÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¢i lÃƒÂ¡Ã‚Â»Ã¢â‚¬Â¹ch sau giÃƒÂ¡Ã‚Â»Ã‚Â check-in.");
    }

    private void ensureCancelAllowed(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Khong the huy lich voi trang thai hien tai.");
        }
        if (nowDateTime().isAfter(LocalDateTime.of(appointment.getDate(), appointment.getStartTime()).minusHours(CANCEL_MIN_HOURS_BEFORE))) {
            throw new BookingException(BookingErrorCode.CANCEL_WINDOW_CLOSED, "Chi co the huy lich truoc gio kham it nhat 24 gio.");
        }
    }

    private LocalDateTime findNextSlotStart(LocalDateTime now) {
        int slotMinute = (now.getMinute() / SLOT_MIN) * SLOT_MIN;
        LocalDateTime currentSlotStart = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), slotMinute));
        return now.isAfter(currentSlotStart) ? currentSlotStart.plusMinutes(SLOT_MIN) : currentSlotStart;
    }

    private boolean isInsideWorkingWindow(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (endDateTime.toLocalDate().isAfter(startDateTime.toLocalDate())) return false;
        LocalTime start = startDateTime.toLocalTime(), end = endDateTime.toLocalTime();
        if (start.isBefore(OPEN) || !end.isAfter(start) || end.isAfter(CLOSE) || start.equals(CLOSE)) return false;
        return !(start.isBefore(LUNCH_END) && end.isAfter(LUNCH_START));
    }

    private LocalDateTime nowDateTime() { return LocalDateTime.now(CLINIC_ZONE); }

    private AppointmentDto toDto(Appointment a) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(a.getId()); dto.setDate(a.getDate()); dto.setStartTime(a.getStartTime()); dto.setEndTime(a.getEndTime());
        dto.setStatus(a.getStatus().name()); dto.setNotes(a.getNotes()); dto.setContactChannel(a.getContactChannel()); dto.setContactValue(a.getContactValue());
        if (a.getService() != null) { dto.setServiceId(a.getService().getId()); dto.setServiceName(a.getService().getName()); }
        if (a.getDentist() != null) { dto.setDentistId(a.getDentist().getId()); dto.setDentistName(a.getDentist().getFullName()); }
        dto.setCanCheckIn(a.getDate().equals(nowDateTime().toLocalDate()) && a.getStatus() == AppointmentStatus.CONFIRMED);
        return dto;
    }
}
