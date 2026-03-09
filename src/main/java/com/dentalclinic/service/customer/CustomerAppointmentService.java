package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.AppointmentServiceItemDto;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.RescheduleAppointmentRequest;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.exception.BookingErrorCode;
import com.dentalclinic.exception.BookingException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.appointment.AppointmentSlot;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private static final BigDecimal DEPOSIT_RATE = new BigDecimal("0.50");

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern VN_PHONE_PATTERN = Pattern.compile("^(0|\\+84)\\d{9,10}$");
    private static final List<String> ACTIVE_BOOKING_STATUSES = List.of(
            AppointmentStatus.PENDING.name(),
            AppointmentStatus.PENDING_DEPOSIT.name(),
            AppointmentStatus.CONFIRMED.name(),
            AppointmentStatus.CHECKED_IN.name(),
            AppointmentStatus.EXAMINING.name(),
            AppointmentStatus.IN_PROGRESS.name()
    );

    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingNoteRepository billingNoteRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final NotificationService notificationService;
    private final WalletService walletService;

    public CustomerAppointmentService(CustomerProfileRepository customerProfileRepository,
                                      UserRepository userRepository,
                                      AppointmentRepository appointmentRepository,
                                      BillingNoteRepository billingNoteRepository,
                                      ServiceRepository serviceRepository,
                                      SlotRepository slotRepository,
                                      NotificationService notificationService,
                                      WalletService walletService) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.billingNoteRepository = billingNoteRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
        this.notificationService = notificationService;
        this.walletService = walletService;
    }

    public static int calculateSlotsNeeded(int durationMinutes) {
        if (durationMinutes <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) durationMinutes / SLOT_MIN);
    }

    public List<SlotDto> getAvailableSlots(Long userId, Long serviceId, LocalDate date) {
        if (serviceId == null) {
            return new ArrayList<>();
        }
        return getAvailableSlots(userId, List.of(serviceId), date);
    }

    public List<SlotDto> getAvailableSlots(Long userId, List<Long> serviceIds, LocalDate date) {
        validateBookingUser(userId);
        validateSelectedDate(date);

        if (serviceIds == null || serviceIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> normalizedServiceIds = normalizeAndValidateServiceIds(serviceIds, false);
        List<Services> services = validateServices(normalizedServiceIds);
        BookingTotals totals = calculateBookingTotals(services);

        int slotsNeeded = calculateSlotsNeeded(totals.totalDurationMinutes());
        List<Slot> slots = getAvailableSlotsForDuration(date, totals.totalDurationMinutes());
        return slots.stream().map(s -> {
            SlotDto dto = toSlotDto(s);
            LocalDateTime end = s.getSlotTime().plusMinutes((long) slotsNeeded * SLOT_MIN);
            dto.setDisabled(hasCustomerOverlap(
                    userId,
                    s.getSlotTime().toLocalDate(),
                    s.getSlotTime().toLocalTime(),
                    end.toLocalTime(),
                    null
            ));
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

        List<Long> requestedServiceIds = normalizeAndValidateServiceIds(request.getResolvedServiceIds(), true);
        List<Services> selectedServices = validateServices(requestedServiceIds);
        BookingTotals totals = calculateBookingTotals(selectedServices);

        validateClientDeposit(request.getDepositAmount(), totals.depositAmount());

        int slotsNeeded = calculateSlotsNeeded(totals.totalDurationMinutes());
        LocalDateTime start = resolveStartDateTime(request);
        LocalDateTime end = start.plusMinutes((long) slotsNeeded * SLOT_MIN);

        validateNotInPast(start);
        validateWorkingWindow(start, end);
        validateNoCustomerOverlap(user.getId(), start.toLocalDate(), start.toLocalTime(), end.toLocalTime(), null);

        List<Slot> reserved = reserveSlots(start, slotsNeeded);
        if (reserved.isEmpty()) {
            throw new BookingException(BookingErrorCode.SLOT_FULL, "Khung giờ đã hết chỗ.");
        }

        Appointment created = createPendingAppointment(
                customer,
                selectedServices,
                totals,
                reserved,
                request.getContactChannel().trim().toUpperCase(),
                request.getContactValue().trim(),
                request.getPatientNote()
        );
        return toDto(created);
    }

    @Transactional
    public AppointmentDto rescheduleAppointment(Long userId, Long appointmentId, RescheduleAppointmentRequest request) {
        if (request == null || request.getSelectedDate() == null || request.getSelectedTime() == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ngày và giờ đổi lịch là bắt buộc.");
        }

        Appointment appointment = appointmentRepository.findByIdWithSlotsAndCustomerUserId(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        ensureRescheduleAllowed(appointment);

        int totalDuration = resolveAppointmentDuration(appointment);
        int slotsNeeded = calculateSlotsNeeded(totalDuration);

        LocalDateTime newStart = LocalDateTime.of(request.getSelectedDate(), request.getSelectedTime());
        LocalDateTime newEnd = newStart.plusMinutes((long) slotsNeeded * SLOT_MIN);

        validateNotInPast(newStart);
        validateWorkingWindow(newStart, newEnd);
        validateNoCustomerOverlap(userId, newStart.toLocalDate(), newStart.toLocalTime(), newEnd.toLocalTime(), appointment.getId());

        List<Slot> oldSlots = appointment.getAppointmentSlots().stream()
                .map(AppointmentSlot::getSlot)
                .collect(Collectors.toCollection(ArrayList::new));

        List<Slot> newSlots = reserveSlots(newStart, slotsNeeded);
        if (newSlots.isEmpty()) {
            throw new BookingException(BookingErrorCode.SLOT_FULL, "Khung giờ đã hết chỗ.");
        }

        appointment.setDate(newStart.toLocalDate());
        appointment.setStartTime(newStart.toLocalTime());
        appointment.setEndTime(newEnd.toLocalTime());
        appointment.clearAppointmentSlots();
        for (int i = 0; i < newSlots.size(); i++) {
            appointment.addAppointmentSlot(new AppointmentSlot(appointment, newSlots.get(i), i));
        }

        Appointment saved = appointmentRepository.save(appointment);
        releaseSlots(oldSlots);

        notificationService.notifyBookingUpdated(saved, "Thay đổi thời gian khám");
        return toDto(saved);
    }

    @Transactional
    public AppointmentDto confirmAppointment(Long appointmentId) {
        return toDto(confirmAppointmentEntity(appointmentId));
    }

    @Transactional
    public AppointmentDto cancelAppointment(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithSlotsAndCustomerUserId(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        ensureCancelAllowed(appointment);

        List<Slot> toRelease = appointment.getAppointmentSlots().stream()
                .map(AppointmentSlot::getSlot)
                .collect(Collectors.toCollection(ArrayList::new));

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment savedAppt = appointmentRepository.save(appointment);
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
        Appointment appointment = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể hủy lịch với trạng thái hiện tại.");
        }

        List<Slot> toRelease = appointment.getAppointmentSlots().stream()
                .map(AppointmentSlot::getSlot)
                .collect(Collectors.toCollection(ArrayList::new));

        releaseSlots(toRelease);
        appointmentRepository.delete(appointment);
        return appointment;
    }

    @Transactional
    public Appointment markDepositPaymentSuccess(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể xác nhận thanh toán cho lịch hẹn này.");
        }

        appointment.setStatus(AppointmentStatus.PENDING);
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.notifyBookingCreated(saved);
        return saved;
    }

    public List<AppointmentDto> getMyAppointments(Long userId) {
        return appointmentRepository.findByCustomer_User_IdOrderByDateDesc(userId).stream()
                .filter(a -> a.getStatus() != AppointmentStatus.PENDING_DEPOSIT)
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public Page<AppointmentDto> getMyAppointmentsPage(Long userId, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);

        List<AppointmentDto> allDtos = appointmentRepository.findByCustomer_User_IdOrderByDateDesc(userId).stream()
                .filter(a -> a.getStatus() != AppointmentStatus.PENDING_DEPOSIT)
                .map(this::toDto)
                .collect(Collectors.toList());

        int fromIndex = Math.min(safePage * safeSize, allDtos.size());
        int toIndex = Math.min(fromIndex + safeSize, allDtos.size());

        return new PageImpl<>(
                allDtos.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("startTime"))),
                allDtos.size()
        );
    }

    public Optional<AppointmentDto> getAppointmentDetail(Long userId, Long appointmentId) {
        return appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId).map(this::toDto);
    }

    @Transactional
    public Optional<AppointmentDto> checkIn(Long userId, Long appointmentId) {
        Optional<Appointment> opt = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId);
        if (opt.isEmpty()) {
            return Optional.empty();
        }

        Appointment appointment = opt.get();
        if (!appointment.getDate().equals(nowDateTime().toLocalDate()) || appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            return Optional.empty();
        }

        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        return Optional.of(toDto(appointmentRepository.save(appointment)));
    }

    public boolean checkDentistOverlap(Long dentistProfileId,
                                       LocalDate date,
                                       LocalTime startTime,
                                       LocalTime endTime,
                                       Long excludeAppointmentId) {
        return excludeAppointmentId == null
                ? appointmentRepository.hasOverlappingAppointment(dentistProfileId, date, startTime, endTime)
                : appointmentRepository.hasOverlappingAppointmentExcludingSelf(dentistProfileId, date, startTime, endTime, excludeAppointmentId);
    }

    private List<Slot> getAvailableSlotsForDuration(LocalDate date, int durationMinutes) {
        int slotsNeeded = calculateSlotsNeeded(durationMinutes);
        LocalDateTime now = nowDateTime();
        LocalDateTime dayStart = LocalDateTime.of(date, OPEN);
        LocalDateTime dayEnd = LocalDateTime.of(date, CLOSE);

        if (date.isBefore(now.toLocalDate()) || dayEnd.isBefore(now) || slotsNeeded <= 0) {
            return new ArrayList<>();
        }

        List<Slot> all = date.equals(now.toLocalDate())
                ? slotRepository.findAvailableSlotsForToday(findNextSlotStart(now), dayEnd)
                : slotRepository.findAvailableSlotsBetweenTimes(dayStart, dayEnd);

        List<Slot> availableStarts = new ArrayList<>();
        for (int i = 0; i <= all.size() - slotsNeeded; i++) {
            boolean good = true;
            for (int j = 0; j < slotsNeeded; j++) {
                Slot slot = all.get(i + j);
                LocalDateTime expectedTime = all.get(i).getSlotTime().plusMinutes((long) j * SLOT_MIN);
                if (!slot.getSlotTime().equals(expectedTime) || !slot.isAvailable()) {
                    good = false;
                    break;
                }
            }
            if (good) {
                LocalDateTime start = all.get(i).getSlotTime();
                if (isInsideWorkingWindow(start, start.plusMinutes((long) slotsNeeded * SLOT_MIN))) {
                    availableStarts.add(all.get(i));
                }
            }
        }
        return availableStarts;
    }

    private List<Slot> getAllSlotsEntitiesForDate(LocalDate date) {
        LocalDateTime now = nowDateTime();
        LocalDateTime dayStart = LocalDateTime.of(date, OPEN);
        LocalDateTime dayEnd = LocalDateTime.of(date, CLOSE);

        if (date.isBefore(now.toLocalDate()) || dayEnd.isBefore(now)) {
            return new ArrayList<>();
        }

        List<Slot> list = date.equals(now.toLocalDate())
                ? slotRepository.findAllSlotsForToday(findNextSlotStart(now), dayEnd)
                : slotRepository.findAllSlotsBetweenTimes(dayStart, dayEnd);

        return list.stream()
                .filter(slot -> isInsideWorkingWindow(slot.getSlotTime(), slot.getSlotTime().plusMinutes(SLOT_MIN)))
                .collect(Collectors.toList());
    }

    @Transactional
    private List<Slot> reserveSlots(LocalDateTime startDateTime, int slotsNeeded) {
        if (!startDateTime.isAfter(nowDateTime())) {
            throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "Không thể đặt lịch trong quá khứ.");
        }
        if (slotsNeeded <= 0) {
            throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "Thời lượng đặt lịch không hợp lệ.");
        }

        LocalDateTime endDateTime = startDateTime.plusMinutes((long) slotsNeeded * SLOT_MIN);
        if (!isInsideWorkingWindow(startDateTime, endDateTime)) {
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Thời gian nằm ngoài giờ làm việc.");
        }

        List<Slot> locked = slotRepository.findActiveSlotsForUpdate(startDateTime, endDateTime);
        if (locked.size() != slotsNeeded) {
            return new ArrayList<>();
        }

        for (int i = 0; i < locked.size(); i++) {
            LocalDateTime expectedTime = startDateTime.plusMinutes((long) i * SLOT_MIN);
            if (!locked.get(i).getSlotTime().equals(expectedTime) || !locked.get(i).isAvailable()) {
                return new ArrayList<>();
            }
        }

        for (Slot slot : locked) {
            slot.setBookedCount(slot.getBookedCount() + 1);
            slotRepository.save(slot);
        }

        return locked;
    }

    @Transactional
    private void releaseSlots(List<Slot> slots) {
        for (Slot slot : slots) {
            Slot locked = slotRepository.findBySlotTimeAndActiveTrueForUpdate(slot.getSlotTime())
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giờ không tồn tại."));

            if (locked.getBookedCount() <= 0) {
                throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Dữ liệu slot không hợp lệ.");
            }

            locked.setBookedCount(locked.getBookedCount() - 1);
            slotRepository.save(locked);
        }
    }

    @Transactional
    private Appointment createPendingAppointment(CustomerProfile customer,
                                                 List<Services> selectedServices,
                                                 BookingTotals totals,
                                                 List<Slot> reservedSlots,
                                                 String contactChannel,
                                                 String contactValue,
                                                 String notes) {
        if (reservedSlots.isEmpty()) {
            throw new BookingException(BookingErrorCode.SLOT_FULL, "Không có slot được giữ.");
        }

        Slot first = reservedSlots.get(0);
        Slot last = reservedSlots.get(reservedSlots.size() - 1);

        Appointment appointment = resolveAppointmentEntityForCreate(customer, first.getSlotTime().toLocalDate(), first.getStartTime());
        appointment.setService(selectedServices.get(0));

        appointment.setTotalDurationMinutes(totals.totalDurationMinutes());
        appointment.setTotalAmount(totals.totalAmount());
        appointment.setDepositAmount(totals.depositAmount());

        appointment.setDate(first.getSlotTime().toLocalDate());
        appointment.setStartTime(first.getStartTime());
        appointment.setEndTime(last.getEndTime());

        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setContactChannel(contactChannel);
        appointment.setContactValue(contactValue);
        appointment.setNotes(notes != null ? notes.trim() : null);

        for (int i = 0; i < reservedSlots.size(); i++) {
            appointment.addAppointmentSlot(new AppointmentSlot(appointment, reservedSlots.get(i), i));
        }

        for (int i = 0; i < selectedServices.size(); i++) {
            appointment.addAppointmentDetail(buildAppointmentDetail(selectedServices.get(i), i));
        }

        return appointmentRepository.save(appointment);
    }

    private Appointment resolveAppointmentEntityForCreate(CustomerProfile customer, LocalDate date, LocalTime startTime) {
        return appointmentRepository.findByCustomer_IdAndDateAndStartTimeAndStatus(
                        customer.getId(),
                        date,
                        startTime,
                        AppointmentStatus.CANCELLED
                )
                .filter(appointment -> billingNoteRepository.findByAppointment_Id(appointment.getId()).isEmpty())
                .map(appointment -> resetCancelledAppointmentForReuse(appointment, customer))
                .orElseGet(() -> {
                    Appointment appointment = new Appointment();
                    appointment.setCustomer(customer);
                    return appointment;
                });
    }

    private Appointment resetCancelledAppointmentForReuse(Appointment appointment, CustomerProfile customer) {
        appointment.clearAppointmentSlots();
        appointment.clearAppointmentDetails();
        appointment.setCustomer(customer);
        appointment.setDentist(null);
        appointment.setService(null);
        appointment.setSlot(null);
        appointment.setOriginalAppointment(null);
        appointment.setCreatedAt(nowDateTime());
        return appointment;
    }

    @Transactional
    private Appointment confirmAppointmentEntity(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.PENDING_DEPOSIT) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể xác nhận lịch hẹn ở trạng thái hiện tại.");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    private Appointment cancelAppointmentEntity(Long appointmentId, String reason, boolean refundIfEligible) {
        Appointment appointment = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể hủy lịch với trạng thái hiện tại.");
        }

        if (refundIfEligible && (appointment.getStatus() == AppointmentStatus.PENDING || appointment.getStatus() == AppointmentStatus.CONFIRMED)) {
            BigDecimal refundAmount = resolveAppointmentDepositAmount(appointment);
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                walletService.refund(
                        appointment.getCustomer(),
                        refundAmount,
                        "Hoàn tiền đặt cọc lịch hẹn #" + appointment.getId(),
                        appointment.getId()
                );
            }
        }

        List<Slot> toRelease = appointment.getAppointmentSlots().stream()
                .map(AppointmentSlot::getSlot)
                .collect(Collectors.toCollection(ArrayList::new));

        appointment.setStatus(AppointmentStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            appointment.setNotes(reason.trim());
        }

        Appointment saved = appointmentRepository.save(appointment);
        releaseSlots(toRelease);
        return saved;
    }

    private AppointmentDetail buildAppointmentDetail(Services service, int order) {
        AppointmentDetail detail = new AppointmentDetail();
        detail.setService(service);
        detail.setServiceNameSnapshot(service.getName());
        detail.setPriceSnapshot(BigDecimal.valueOf(service.getPrice()).setScale(2, RoundingMode.HALF_UP));
        detail.setDurationSnapshot(service.getDurationMinutes());
        detail.setDetailOrder(order);
        return detail;
    }

    private SlotDto toSlotDto(Slot slot) {
        SlotDto dto = new SlotDto();
        dto.setId(slot.getId());
        dto.setDate(slot.getSlotTime().toLocalDate());
        dto.setStartTime(slot.getSlotTime().toLocalTime());
        dto.setEndTime(slot.getSlotTime().toLocalTime().plusMinutes(SLOT_MIN));
        dto.setAvailable(slot.isAvailable());
        dto.setCapacity(slot.getCapacity());
        dto.setBookedCount(slot.getBookedCount());
        dto.setAvailableSpots(slot.getAvailableSpots());
        return dto;
    }

    private void validateCreateRequest(CreateAppointmentRequest request) {
        if (request == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Dữ liệu đặt lịch không hợp lệ.");
        }

        List<Long> rawServiceIds = request.getResolvedServiceIds();
        normalizeAndValidateServiceIds(rawServiceIds, true);

        if (!request.isOldFormat() && !request.isNewFormat()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng chọn ngày và giờ hợp lệ.");
        }

        if (request.getPatientNote() != null && request.getPatientNote().length() > 500) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ghi chú tối đa 500 ký tự.");
        }

        if (request.getContactChannel() == null || request.getContactChannel().isBlank()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng chọn kênh liên hệ.");
        }
        if (request.getContactValue() == null || request.getContactValue().isBlank()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng nhập thông tin liên hệ.");
        }

        String channel = request.getContactChannel().trim().toUpperCase();
        String value = request.getContactValue().trim();

        if (("PHONE".equals(channel) || "ZALO".equals(channel)) && !VN_PHONE_PATTERN.matcher(value.replaceAll("\\s+", "")).matches()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Số điện thoại/Zalo không hợp lệ.");
        }
        if ("EMAIL".equals(channel) && !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Email không hợp lệ.");
        }
        if (!List.of("PHONE", "ZALO", "EMAIL").contains(channel)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Kênh liên hệ chỉ được: PHONE, ZALO, EMAIL.");
        }

        if (request.getDepositAmount() != null && request.getDepositAmount() < 0) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Tiền cọc không được âm.");
        }

        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            String status = request.getStatus().trim().toUpperCase();
            if (!"PENDING".equals(status) && !"CONFIRMED".equals(status)) {
                throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID,
                        "Chỉ được khởi tạo với trạng thái PENDING hoặc CONFIRMED.");
            }
        }
    }

    private List<Long> normalizeAndValidateServiceIds(List<Long> rawServiceIds, boolean strictRequired) {
        List<Long> ids = new ArrayList<>();
        if (rawServiceIds != null) {
            ids.addAll(rawServiceIds);
        }

        if (strictRequired && ids.isEmpty()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng chọn ít nhất một dịch vụ.");
        }

        HashSet<Long> seen = new HashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Mã dịch vụ không hợp lệ.");
            }
            if (!seen.add(id)) {
                throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Không được chọn trùng dịch vụ.");
            }
        }

        return ids;
    }

    private User validateBookingUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "Người dùng không tồn tại."));

        if (user.getRole() != Role.CUSTOMER) {
            throw new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "Chỉ bệnh nhân mới được đặt lịch.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BookingException(BookingErrorCode.USER_NOT_ALLOWED, "Tài khoản đang bị khóa.");
        }

        return user;
    }

    private CustomerProfile getOrCreateCustomerProfile(User user) {
        return customerProfileRepository.findByUser_Id(user.getId()).orElseGet(() -> {
            CustomerProfile profile = new CustomerProfile();
            profile.setUser(user);
            profile.setFullName(user.getEmail() != null ? user.getEmail() : "Khách hàng");
            return customerProfileRepository.save(profile);
        });
    }

    private List<Services> validateServices(List<Long> serviceIds) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            throw new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "Danh sách dịch vụ không hợp lệ.");
        }

        Map<Long, Services> serviceMap = serviceRepository.findAllById(serviceIds)
                .stream()
                .collect(Collectors.toMap(Services::getId, s -> s));

        List<Services> services = new ArrayList<>();
        for (Long serviceId : serviceIds) {
            Services service = serviceMap.get(serviceId);
            if (service == null) {
                throw new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "Dịch vụ không tồn tại.");
            }
            if (!service.isActive()) {
                throw new BookingException(BookingErrorCode.SERVICE_INACTIVE, "Dịch vụ đang tạm ngưng.");
            }
            if (service.getDurationMinutes() <= 0) {
                throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "Thời lượng dịch vụ không hợp lệ.");
            }
            services.add(service);
        }

        return services;
    }

    private BookingTotals calculateBookingTotals(List<Services> services) {
        int totalDuration = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (Services service : services) {
            totalDuration += service.getDurationMinutes();
            totalAmount = totalAmount.add(BigDecimal.valueOf(service.getPrice()));
        }

        totalAmount = totalAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal depositAmount = totalAmount.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
        return new BookingTotals(totalDuration, totalAmount, depositAmount);
    }

    private void validateClientDeposit(Double clientDeposit, BigDecimal expectedDeposit) {
        if (clientDeposit == null) {
            return;
        }

        BigDecimal client = BigDecimal.valueOf(clientDeposit).setScale(2, RoundingMode.HALF_UP);
        if (client.compareTo(expectedDeposit) != 0) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Tiền cọc không hợp lệ theo cấu hình booking.");
        }
    }

    private int resolveAppointmentDuration(Appointment appointment) {
        if (appointment.getTotalDurationMinutes() != null && appointment.getTotalDurationMinutes() > 0) {
            return appointment.getTotalDurationMinutes();
        }

        List<AppointmentDetail> details = appointment.getAppointmentDetails();
        if (details != null && !details.isEmpty()) {
            int sum = details.stream()
                    .map(AppointmentDetail::getDurationSnapshot)
                    .filter(v -> v != null && v > 0)
                    .mapToInt(Integer::intValue)
                    .sum();
            if (sum > 0) {
                return sum;
            }
        }

        if (appointment.getService() != null && appointment.getService().getDurationMinutes() > 0) {
            return appointment.getService().getDurationMinutes();
        }

        throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "Không xác định được thời lượng lịch hẹn.");
    }

    private BigDecimal resolveAppointmentDepositAmount(Appointment appointment) {
        if (appointment.getDepositAmount() != null) {
            return appointment.getDepositAmount().setScale(2, RoundingMode.HALF_UP);
        }

        if (appointment.getTotalAmount() != null) {
            return appointment.getTotalAmount().multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
        }

        if (appointment.getService() != null) {
            return BigDecimal.valueOf(appointment.getService().getPrice())
                    .multiply(DEPOSIT_RATE)
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private LocalDateTime resolveStartDateTime(CreateAppointmentRequest request) {
        if (request.isOldFormat()) {
            Slot slot = slotRepository.findById(request.getSlotId())
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giờ không tồn tại."));
            return slot.getSlotTime();
        }
        return LocalDateTime.of(request.getSelectedDate(), request.getSelectedTime());
    }

    private void validateSelectedDate(LocalDate date) {
        if (date == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ngày không hợp lệ.");
        }
        if (date.isBefore(nowDateTime().toLocalDate())) {
            throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "Không thể đặt lịch trong quá khứ.");
        }
    }

    private void validateNotInPast(LocalDateTime startDateTime) {
        if (!startDateTime.isAfter(nowDateTime())) {
            throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "Không thể đặt lịch trong quá khứ.");
        }
    }

    private void validateWorkingWindow(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        LocalTime start = startDateTime.toLocalTime();
        LocalTime end = endDateTime.toLocalTime();

        if (endDateTime.toLocalDate().isAfter(startDateTime.toLocalDate())) {
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Thời gian nằm ngoài giờ làm việc.");
        }

        if (start.isBefore(OPEN) || !end.isAfter(start) || end.isAfter(CLOSE) || start.equals(CLOSE)) {
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Thời gian nằm ngoài giờ làm việc.");
        }

        if (start.isBefore(LUNCH_END) && end.isAfter(LUNCH_START)) {
            throw new BookingException(BookingErrorCode.LUNCH_BREAK_CONFLICT, "Khung giờ cắt ngang thời gian nghỉ trưa 12:00-13:00.");
        }
    }

    private void validateNoCustomerOverlap(Long userId,
                                           LocalDate date,
                                           LocalTime startTime,
                                           LocalTime endTime,
                                           Long excludeAppointmentId) {
        if (hasCustomerOverlap(userId, date, startTime, endTime, excludeAppointmentId)) {
            throw new BookingException(BookingErrorCode.BOOKING_CONFLICT, "Bạn đã có lịch hẹn trùng thời điểm này.");
        }
    }

    private boolean hasCustomerOverlap(Long userId,
                                       LocalDate date,
                                       LocalTime startTime,
                                       LocalTime endTime,
                                       Long excludeAppointmentId) {
        return excludeAppointmentId == null
                ? appointmentRepository.existsCustomerOverlap(userId, date, startTime, endTime, ACTIVE_BOOKING_STATUSES)
                : appointmentRepository.existsCustomerOverlapExcludingAppointment(userId, excludeAppointmentId, date, startTime, endTime, ACTIVE_BOOKING_STATUSES);
    }

    private void ensureRescheduleAllowed(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BookingException(BookingErrorCode.RESCHEDULE_NOT_ALLOWED, "Không thể đổi lịch với trạng thái hiện tại.");
        }

        if (!LocalDateTime.of(appointment.getDate(), appointment.getStartTime()).isAfter(nowDateTime())) {
            throw new BookingException(BookingErrorCode.RESCHEDULE_NOT_ALLOWED, "Không thể đổi lịch sau giờ check-in.");
        }
    }

    private void ensureCancelAllowed(Appointment appointment) {
        if (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể hủy lịch với trạng thái hiện tại.");
        }

        LocalDateTime threshold = LocalDateTime.of(appointment.getDate(), appointment.getStartTime())
                .minusHours(CANCEL_MIN_HOURS_BEFORE);
        if (nowDateTime().isAfter(threshold)) {
            throw new BookingException(BookingErrorCode.CANCEL_WINDOW_CLOSED, "Chỉ có thể hủy lịch trước giờ khám ít nhất 24 giờ.");
        }
    }

    private LocalDateTime findNextSlotStart(LocalDateTime now) {
        int slotMinute = (now.getMinute() / SLOT_MIN) * SLOT_MIN;
        LocalDateTime currentSlotStart = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), slotMinute));
        return now.isAfter(currentSlotStart) ? currentSlotStart.plusMinutes(SLOT_MIN) : currentSlotStart;
    }

    private boolean isInsideWorkingWindow(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (endDateTime.toLocalDate().isAfter(startDateTime.toLocalDate())) {
            return false;
        }

        LocalTime start = startDateTime.toLocalTime();
        LocalTime end = endDateTime.toLocalTime();

        if (start.isBefore(OPEN) || !end.isAfter(start) || end.isAfter(CLOSE) || start.equals(CLOSE)) {
            return false;
        }

        return !(start.isBefore(LUNCH_END) && end.isAfter(LUNCH_START));
    }

    private LocalDateTime nowDateTime() {
        return LocalDateTime.now(CLINIC_ZONE);
    }

    private AppointmentDto toDto(Appointment appointment) {
        AppointmentDto dto = new AppointmentDto();
        dto.setId(appointment.getId());
        dto.setDate(appointment.getDate());
        dto.setStartTime(appointment.getStartTime());
        dto.setEndTime(appointment.getEndTime());
        dto.setStatus(appointment.getStatus().name());
        dto.setNotes(appointment.getNotes());
        dto.setContactChannel(appointment.getContactChannel());
        dto.setContactValue(appointment.getContactValue());

        List<AppointmentServiceItemDto> serviceItems = new ArrayList<>();
        List<AppointmentDetail> details = appointment.getAppointmentDetails();
        if (details != null && !details.isEmpty()) {
            for (AppointmentDetail detail : details) {
                AppointmentServiceItemDto item = new AppointmentServiceItemDto();
                item.setServiceId(detail.getService() != null ? detail.getService().getId() : null);
                item.setServiceName(detail.getServiceNameSnapshot());
                item.setDurationMinutes(detail.getDurationSnapshot());
                item.setPrice(detail.getPriceSnapshot());
                serviceItems.add(item);
            }
        } else if (appointment.getService() != null) {
            AppointmentServiceItemDto item = new AppointmentServiceItemDto();
            item.setServiceId(appointment.getService().getId());
            item.setServiceName(appointment.getService().getName());
            item.setDurationMinutes(appointment.getService().getDurationMinutes());
            item.setPrice(BigDecimal.valueOf(appointment.getService().getPrice()).setScale(2, RoundingMode.HALF_UP));
            serviceItems.add(item);
        }

        dto.setServices(serviceItems);
        dto.setServiceIds(serviceItems.stream()
                .map(AppointmentServiceItemDto::getServiceId)
                .filter(id -> id != null)
                .collect(Collectors.toList()));

        if (!serviceItems.isEmpty()) {
            AppointmentServiceItemDto first = serviceItems.get(0);
            dto.setServiceId(first.getServiceId());
            dto.setServiceName(serviceItems.stream()
                    .map(AppointmentServiceItemDto::getServiceName)
                    .filter(name -> name != null && !name.isBlank())
                    .collect(Collectors.joining(", ")));
        }

        int duration = appointment.getTotalDurationMinutes() != null ? appointment.getTotalDurationMinutes() : 0;
        if (duration <= 0) {
            duration = serviceItems.stream()
                    .map(AppointmentServiceItemDto::getDurationMinutes)
                    .filter(v -> v != null && v > 0)
                    .mapToInt(Integer::intValue)
                    .sum();
        }
        dto.setTotalDurationMinutes(duration > 0 ? duration : null);

        BigDecimal totalAmount = appointment.getTotalAmount();
        if (totalAmount == null) {
            totalAmount = serviceItems.stream()
                    .map(AppointmentServiceItemDto::getPrice)
                    .filter(price -> price != null)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
        dto.setTotalAmount(totalAmount != null ? totalAmount.setScale(2, RoundingMode.HALF_UP) : null);
        dto.setDepositAmount(resolveAppointmentDepositAmount(appointment));

        if (appointment.getDentist() != null) {
            dto.setDentistId(appointment.getDentist().getId());
            dto.setDentistName(appointment.getDentist().getFullName());
        }

        dto.setCanCheckIn(appointment.getDate().equals(nowDateTime().toLocalDate()) && appointment.getStatus() == AppointmentStatus.CONFIRMED);
        return dto;
    }

    private record BookingTotals(int totalDurationMinutes, BigDecimal totalAmount, BigDecimal depositAmount) {
    }
}
