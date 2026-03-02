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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private static final int CANCEL_MIN_HOURS_BEFORE = 2;
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

    public CustomerAppointmentService(CustomerProfileRepository customerProfileRepository,
                                      UserRepository userRepository,
                                      AppointmentRepository appointmentRepository,
                                      ServiceRepository serviceRepository,
                                      SlotRepository slotRepository,
                                      NotificationService notificationService) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
        this.notificationService = notificationService;
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
        if (reserved.isEmpty()) throw new BookingException(BookingErrorCode.SLOT_FULL, "Slot Ä‘Ã£ háº¿t chá»—.");
        Appointment created = createPendingAppointment(customer, service, reserved,
                request.getContactChannel().trim().toUpperCase(), request.getContactValue().trim(), request.getPatientNote());
        notificationService.notifyBookingCreated(created);
        return toDto(created);
    }

    @Transactional
    public AppointmentDto rescheduleAppointment(Long userId, Long appointmentId, RescheduleAppointmentRequest request) {
        if (request == null || request.getSelectedDate() == null || request.getSelectedTime() == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "NgÃ y vÃ  giá» Ä‘á»•i lá»‹ch lÃ  báº¯t buá»™c.");
        }
        Appointment a = appointmentRepository.findByIdWithSlotsAndCustomerUserId(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lá»‹ch háº¹n khÃ´ng tá»“n táº¡i."));
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
        if (newSlots.isEmpty()) throw new BookingException(BookingErrorCode.SLOT_FULL, "Slot Ä‘Ã£ háº¿t chá»—.");

        a.setDate(newStart.toLocalDate());
        a.setStartTime(newStart.toLocalTime());
        a.setEndTime(newEnd.toLocalTime());
        a.clearAppointmentSlots();
        for (int i = 0; i < newSlots.size(); i++) a.addAppointmentSlot(new AppointmentSlot(a, newSlots.get(i), i));
        Appointment saved = appointmentRepository.save(a);
        releaseSlots(oldSlots);
        notificationService.notifyBookingUpdated(saved, "Thay đổi thời gian khám");
        return toDto(saved);
    }

    @Transactional
    public AppointmentDto confirmAppointment(Long appointmentId) { return toDto(confirmAppointmentEntity(appointmentId)); }

    @Transactional
    public AppointmentDto cancelAppointment(Long userId, Long appointmentId) {
        Appointment a = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lá»‹ch háº¹n khÃ´ng tá»“n táº¡i."));
        ensureCancelAllowed(a);
        return toDto(cancelAppointmentEntity(appointmentId, null));
    }

    @Transactional
    public Appointment cancelAppointmentByStaff(Long appointmentId, String reason) {
        Appointment cancelled = cancelAppointmentEntity(appointmentId, reason);
        notificationService.notifyBookingCancelled(cancelled, reason);
        return cancelled;
    }

    public List<AppointmentDto> getMyAppointments(Long userId) {
        return appointmentRepository.findByCustomer_User_IdOrderByDateDesc(userId).stream().map(this::toDto).collect(Collectors.toList());
    }

    public Page<AppointmentDto> getMyAppointmentsPage(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Order.desc("date"), Sort.Order.desc("startTime")));
        Page<Appointment> appointments = appointmentRepository.findByCustomer_User_Id(userId, pageable);
        List<AppointmentDto> content = appointments.getContent().stream().map(this::toDto).toList();
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
        if (!startDateTime.isAfter(nowDateTime())) throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "KhÃ´ng thá»ƒ Ä‘áº·t lá»‹ch trong quÃ¡ khá»©.");
        if (slotsNeeded <= 0) throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "Thá»i lÆ°á»£ng Ä‘áº·t lá»‹ch khÃ´ng há»£p lá»‡.");
        LocalDateTime endDateTime = startDateTime.plusMinutes((long) slotsNeeded * SLOT_MIN);
        if (!isInsideWorkingWindow(startDateTime, endDateTime)) throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Thá»i gian náº±m ngoÃ i giá» lÃ m viá»‡c.");

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
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giá» khÃ´ng tá»“n táº¡i."));
            if (locked.getBookedCount() <= 0) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Dá»¯ liá»‡u slot khÃ´ng há»£p lá»‡.");
            locked.setBookedCount(locked.getBookedCount() - 1);
            slotRepository.save(locked);
        }
    }

    @Transactional
    private Appointment createPendingAppointment(CustomerProfile customer, Services service, List<Slot> reservedSlots,
                                                 String contactChannel, String contactValue, String notes) {
        if (reservedSlots.isEmpty()) throw new BookingException(BookingErrorCode.SLOT_FULL, "KhÃ´ng cÃ³ slot Ä‘Æ°á»£c giá»¯.");
        Slot first = reservedSlots.get(0), last = reservedSlots.get(reservedSlots.size() - 1);
        Appointment a = new Appointment();
        a.setCustomer(customer); a.setService(service);
        a.setDate(first.getSlotTime().toLocalDate()); a.setStartTime(first.getStartTime()); a.setEndTime(last.getEndTime());
        a.setStatus(AppointmentStatus.PENDING); a.setContactChannel(contactChannel); a.setContactValue(contactValue);
        a.setNotes(notes != null ? notes.trim() : null);
        for (int i = 0; i < reservedSlots.size(); i++) a.addAppointmentSlot(new AppointmentSlot(a, reservedSlots.get(i), i));
        return appointmentRepository.save(a);
    }

    @Transactional
    private Appointment confirmAppointmentEntity(Long appointmentId) {
        Appointment a = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lá»‹ch háº¹n khÃ´ng tá»“n táº¡i."));
        if (a.getStatus() != AppointmentStatus.PENDING && a.getStatus() != AppointmentStatus.PENDING_DEPOSIT) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "KhÃ´ng thá»ƒ xÃ¡c nháº­n lá»‹ch háº¹n á»Ÿ tráº¡ng thÃ¡i hiá»‡n táº¡i.");
        }
        a.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(a);
    }

    @Transactional
    private Appointment cancelAppointmentEntity(Long appointmentId, String reason) {
        Appointment a = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lá»‹ch háº¹n khÃ´ng tá»“n táº¡i."));
        if (a.getStatus() == AppointmentStatus.CANCELLED || a.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "KhÃ´ng thá»ƒ há»§y lá»‹ch vá»›i tráº¡ng thÃ¡i hiá»‡n táº¡i.");
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
        if (request == null) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Dá»¯ liá»‡u Ä‘áº·t lá»‹ch khÃ´ng há»£p lá»‡.");
        if (request.getServiceId() == null || request.getServiceId() <= 0) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lÃ²ng chá»n dá»‹ch vá»¥ há»£p lá»‡.");
        if (!request.isOldFormat() && !request.isNewFormat()) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lÃ²ng chá»n ngÃ y vÃ  giá» há»£p lá»‡.");
        if (request.getPatientNote() != null && request.getPatientNote().length() > 500) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ghi chÃº tá»‘i Ä‘a 500 kÃ½ tá»±.");
        if (request.getContactChannel() == null || request.getContactChannel().isBlank()) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lÃ²ng chá»n kÃªnh liÃªn há»‡.");
        if (request.getContactValue() == null || request.getContactValue().isBlank()) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lÃ²ng nháº­p thÃ´ng tin liÃªn há»‡.");

        String channel = request.getContactChannel().trim().toUpperCase();
        String value = request.getContactValue().trim();
        if (("PHONE".equals(channel) || "ZALO".equals(channel)) && !VN_PHONE_PATTERN.matcher(value.replaceAll("\\s+", "")).matches())
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Sá»‘ Ä‘iá»‡n thoáº¡i/Zalo khÃ´ng há»£p lá»‡.");
        if ("EMAIL".equals(channel) && !EMAIL_PATTERN.matcher(value).matches())
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Email khÃ´ng há»£p lá»‡.");
        if (!List.of("PHONE", "ZALO", "EMAIL").contains(channel))
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "KÃªnh liÃªn há»‡ chá»‰ Ä‘Æ°á»£c: PHONE, ZALO, EMAIL.");
        if (request.getDepositAmount() != null && request.getDepositAmount() < 0)
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Tiá»n cá»c khÃ´ng Ä‘Æ°á»£c Ã¢m.");
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase();
            if (!"PENDING".equals(status) && !"CONFIRMED".equals(status))
                throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Chá»‰ Ä‘Æ°á»£c khá»Ÿi táº¡o vá»›i tráº¡ng thÃ¡i PENDING hoáº·c CONFIRMED.");
        }
    }

    private User validateBookingUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "NgÆ°á»i dÃ¹ng khÃ´ng tá»“n táº¡i."));
        if (user.getRole() != Role.CUSTOMER) throw new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "Chá»‰ bá»‡nh nhÃ¢n má»›i Ä‘Æ°á»£c Ä‘áº·t lá»‹ch.");
        if (user.getStatus() != UserStatus.ACTIVE) throw new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "TÃ i khoáº£n Ä‘ang bá»‹ khÃ³a.");
        return user;
    }

    private CustomerProfile getOrCreateCustomerProfile(User user) {
        return customerProfileRepository.findByUser_Id(user.getId()).orElseGet(() -> {
            CustomerProfile p = new CustomerProfile();
            p.setUser(user); p.setFullName(user.getEmail() != null ? user.getEmail() : "KhÃ¡ch hÃ ng");
            return customerProfileRepository.save(p);
        });
    }

    private Services validateService(Long serviceId) {
        if (serviceId == null || serviceId <= 0) throw new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "Dá»‹ch vá»¥ khÃ´ng tá»“n táº¡i.");
        Services service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "Dá»‹ch vá»¥ khÃ´ng tá»“n táº¡i."));
        if (!service.isActive()) throw new BookingException(BookingErrorCode.SERVICE_INACTIVE, "Dá»‹ch vá»¥ Ä‘ang táº¡m ngÆ°ng.");
        if (service.getDurationMinutes() <= 0) throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "Thá»i lÆ°á»£ng dá»‹ch vá»¥ khÃ´ng há»£p lá»‡.");
        return service;
    }

    private void validateClientDeposit(Double clientDeposit, Services service) {
        if (clientDeposit == null) return;
        double expected = Math.max(0d, service.getPrice() * DEFAULT_DEPOSIT_RATE);
        if (Math.abs(clientDeposit - expected) > 0.0001d)
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Tiá»n cá»c khÃ´ng há»£p lá»‡ theo cáº¥u hÃ¬nh dá»‹ch vá»¥.");
    }

    private LocalDateTime resolveStartDateTime(CreateAppointmentRequest request) {
        if (request.isOldFormat()) {
            Slot slot = slotRepository.findById(request.getSlotId())
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giá» khÃ´ng tá»“n táº¡i."));
            return slot.getSlotTime();
        }
        return LocalDateTime.of(request.getSelectedDate(), request.getSelectedTime());
    }

    private void validateSelectedDate(LocalDate date) {
        if (date == null) throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "NgÃ y khÃ´ng há»£p lá»‡.");
        if (date.isBefore(nowDateTime().toLocalDate())) throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "KhÃ´ng thá»ƒ Ä‘áº·t lá»‹ch trong quÃ¡ khá»©.");
    }

    private void validateNotInPast(LocalDateTime startDateTime) {
        if (!startDateTime.isAfter(nowDateTime())) throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "KhÃ´ng thá»ƒ Ä‘áº·t lá»‹ch trong quÃ¡ khá»©.");
    }

    private void validateWorkingWindow(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        LocalTime start = startDateTime.toLocalTime(), end = endDateTime.toLocalTime();
        if (endDateTime.toLocalDate().isAfter(startDateTime.toLocalDate()))
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Thá»i gian náº±m ngoÃ i giá» lÃ m viá»‡c.");
        if (start.isBefore(OPEN) || !end.isAfter(start) || end.isAfter(CLOSE) || start.equals(CLOSE))
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Thá»i gian náº±m ngoÃ i giá» lÃ m viá»‡c.");
        if (start.isBefore(LUNCH_END) && end.isAfter(LUNCH_START))
            throw new BookingException(BookingErrorCode.LUNCH_BREAK_CONFLICT, "Khung giá» cáº¯t ngang thá»i gian nghá»‰ trÆ°a 12:00-13:00.");
    }

    private void validateNoCustomerOverlap(Long userId, LocalDate date, LocalTime startTime, LocalTime endTime, Long excludeAppointmentId) {
        if (hasCustomerOverlap(userId, date, startTime, endTime, excludeAppointmentId))
            throw new BookingException(BookingErrorCode.BOOKING_CONFLICT, "Báº¡n Ä‘Ã£ cÃ³ lá»‹ch háº¹n trÃ¹ng thá»i Ä‘iá»ƒm nÃ y.");
    }

    private boolean hasCustomerOverlap(Long userId, LocalDate date, LocalTime startTime, LocalTime endTime, Long excludeAppointmentId) {
        return excludeAppointmentId == null
                ? appointmentRepository.existsCustomerOverlap(userId, date, startTime, endTime, ACTIVE_BOOKING_STATUSES)
                : appointmentRepository.existsCustomerOverlapExcludingAppointment(userId, excludeAppointmentId, date, startTime, endTime, ACTIVE_BOOKING_STATUSES);
    }

    private void ensureRescheduleAllowed(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED)
            throw new BookingException(BookingErrorCode.RESCHEDULE_NOT_ALLOWED, "KhÃ´ng thá»ƒ Ä‘á»•i lá»‹ch vá»›i tráº¡ng thÃ¡i hiá»‡n táº¡i.");
        if (!LocalDateTime.of(appointment.getDate(), appointment.getStartTime()).isAfter(nowDateTime()))
            throw new BookingException(BookingErrorCode.RESCHEDULE_NOT_ALLOWED, "KhÃ´ng thá»ƒ Ä‘á»•i lá»‹ch sau giá» check-in.");
    }

    private void ensureCancelAllowed(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED)
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "KhÃ´ng thá»ƒ há»§y lá»‹ch vá»›i tráº¡ng thÃ¡i hiá»‡n táº¡i.");
        if (nowDateTime().isAfter(LocalDateTime.of(appointment.getDate(), appointment.getStartTime()).minusHours(CANCEL_MIN_HOURS_BEFORE)))
            throw new BookingException(BookingErrorCode.CANCEL_WINDOW_CLOSED, "KhÃ´ng thá»ƒ há»§y lá»‹ch trong vÃ²ng 2 giá» trÆ°á»›c giá» háº¹n.");
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
