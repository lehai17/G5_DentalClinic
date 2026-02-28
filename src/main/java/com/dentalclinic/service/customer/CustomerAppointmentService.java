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
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.booking.BookingService;
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
    private static final LocalTime CLINIC_OPEN = LocalTime.of(8, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    private static final LocalTime CLINIC_CLOSE = LocalTime.of(17, 0);
    private static final int SLOT_MINUTES = 30;
    private static final int CANCEL_MIN_HOURS_BEFORE = 2;
    private static final double DEFAULT_DEPOSIT_RATE = 0.0d;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern VN_PHONE_PATTERN =
            Pattern.compile("^(0|\\+84)\\d{9,10}$");

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
    private final BookingService bookingService;

    public CustomerAppointmentService(CustomerProfileRepository customerProfileRepository,
                                      UserRepository userRepository,
                                      AppointmentRepository appointmentRepository,
                                      ServiceRepository serviceRepository,
                                      BookingService bookingService) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.serviceRepository = serviceRepository;
        this.bookingService = bookingService;
    }

    public List<SlotDto> getAvailableSlots(Long userId, Long serviceId, LocalDate date) {
        validateBookingUser(userId);
        Services service = validateService(serviceId);
        validateSelectedDate(date);

        int slotsNeeded = BookingService.calculateSlotsNeeded(service.getDurationMinutes());
        List<Slot> availableSlots = bookingService.getAvailableSlotsForService(date, service.getDurationMinutes());
        return availableSlots.stream().map(slot -> {
            SlotDto dto = toSlotDto(slot);
            LocalDateTime slotStart = slot.getSlotTime();
            LocalDateTime slotEnd = slotStart.plusMinutes((long) slotsNeeded * SLOT_MINUTES);
            boolean overlap = hasCustomerOverlap(userId, slotStart.toLocalDate(), slotStart.toLocalTime(), slotEnd.toLocalTime(), null);
            dto.setDisabled(overlap);
            return dto;
        }).collect(Collectors.toList());
    }

    public List<SlotDto> getAllSlotsForDate(LocalDate date) {
        validateSelectedDate(date);
        List<Slot> slots = bookingService.getAllSlotsForDate(date);
        return slots.stream().map(this::toSlotDto).collect(Collectors.toList());
    }

    @Transactional
    public AppointmentDto createAppointment(Long userId, CreateAppointmentRequest request) {
        validateCreateRequest(request);
        User user = validateBookingUser(userId);
        CustomerProfile customer = getOrCreateCustomerProfile(user);
        Services service = validateService(request.getServiceId());
        validateClientDeposit(request.getDepositAmount(), service);

        int slotsNeeded = BookingService.calculateSlotsNeeded(service.getDurationMinutes());
        LocalDateTime startDateTime = resolveStartDateTime(request);
        LocalDateTime endDateTime = startDateTime.plusMinutes((long) slotsNeeded * SLOT_MINUTES);

        validateNotInPast(startDateTime);
        validateWorkingWindow(startDateTime, endDateTime);
        validateNoCustomerOverlap(user.getId(), startDateTime.toLocalDate(), startDateTime.toLocalTime(), endDateTime.toLocalTime(), null);

        List<Slot> reservedSlots = bookingService.reserveSlots(startDateTime, slotsNeeded);
        if (reservedSlots.isEmpty()) {
            throw new BookingException(BookingErrorCode.SLOT_FULL, "Slot đã hết chỗ.");
        }

        Appointment appointment = bookingService.createPendingAppointment(
                customer,
                service,
                reservedSlots,
                request.getContactChannel().trim().toUpperCase(),
                request.getContactValue().trim(),
                request.getPatientNote()
        );

        return toDto(appointment);
    }

    @Transactional
    public AppointmentDto rescheduleAppointment(Long userId, Long appointmentId, RescheduleAppointmentRequest request) {
        if (request == null || request.getSelectedDate() == null || request.getSelectedTime() == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ngày và giờ đổi lịch là bắt buộc.");
        }

        Appointment appointment = appointmentRepository.findByIdWithSlotsAndCustomerUserId(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        ensureRescheduleAllowed(appointment);

        Services service = appointment.getService();
        if (service == null) {
            throw new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "Dịch vụ không tồn tại.");
        }
        if (!service.isActive()) {
            throw new BookingException(BookingErrorCode.SERVICE_INACTIVE, "Dịch vụ đang tạm ngưng.");
        }

        int slotsNeeded = BookingService.calculateSlotsNeeded(service.getDurationMinutes());
        LocalDateTime newStart = LocalDateTime.of(request.getSelectedDate(), request.getSelectedTime());
        LocalDateTime newEnd = newStart.plusMinutes((long) slotsNeeded * SLOT_MINUTES);

        validateNotInPast(newStart);
        validateWorkingWindow(newStart, newEnd);
        validateNoCustomerOverlap(userId, newStart.toLocalDate(), newStart.toLocalTime(), newEnd.toLocalTime(), appointment.getId());

        List<Slot> oldSlots = appointment.getAppointmentSlots().stream()
                .map(AppointmentSlot::getSlot)
                .collect(Collectors.toCollection(ArrayList::new));

        List<Slot> newSlots = bookingService.reserveSlots(newStart, slotsNeeded);
        if (newSlots.isEmpty()) {
            throw new BookingException(BookingErrorCode.SLOT_FULL, "Slot đã hết chỗ.");
        }

        appointment.setDate(newStart.toLocalDate());
        appointment.setStartTime(newStart.toLocalTime());
        appointment.setEndTime(newEnd.toLocalTime());
        appointment.clearAppointmentSlots();
        for (int i = 0; i < newSlots.size(); i++) {
            appointment.addAppointmentSlot(new AppointmentSlot(appointment, newSlots.get(i), i));
        }

        Appointment saved = appointmentRepository.save(appointment);
        bookingService.releaseSlots(oldSlots);
        return toDto(saved);
    }

    @Transactional
    public AppointmentDto confirmAppointment(Long appointmentId) {
        Appointment appointment = bookingService.confirmAppointment(appointmentId);
        return toDto(appointment);
    }

    @Transactional
    public AppointmentDto cancelAppointment(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        ensureCancelAllowed(appointment);
        Appointment cancelled = bookingService.cancelAppointment(appointmentId);
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
        LocalDate today = nowDateTime().toLocalDate();
        if (!a.getDate().equals(today) || a.getStatus() != AppointmentStatus.CONFIRMED) {
            return Optional.empty();
        }

        a.setStatus(AppointmentStatus.CHECKED_IN);
        appointmentRepository.save(a);
        return Optional.of(toDto(a));
    }

    private SlotDto toSlotDto(Slot slot) {
        SlotDto dto = new SlotDto();
        dto.setId(slot.getId());
        dto.setDate(slot.getSlotTime().toLocalDate());
        dto.setStartTime(slot.getSlotTime().toLocalTime());
        dto.setEndTime(slot.getSlotTime().toLocalTime().plusMinutes(SLOT_MINUTES));
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
        if (request.getServiceId() == null || request.getServiceId() <= 0) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng chọn dịch vụ hợp lệ.");
        }

        if (!request.isOldFormat() && !request.isNewFormat()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng chọn ngày và giờ hợp lệ.");
        }

        if (request.getPatientNote() != null && request.getPatientNote().length() > 500) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ghi chú tối đa 500 ký tự.");
        }

        String channel = request.getContactChannel();
        String value = request.getContactValue();
        if (channel == null || channel.isBlank()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng chọn kênh liên hệ.");
        }
        if (value == null || value.isBlank()) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng nhập thông tin liên hệ.");
        }

        String normalizedChannel = channel.trim().toUpperCase();
        String normalizedValue = value.trim();
        switch (normalizedChannel) {
            case "PHONE":
            case "ZALO":
                if (!VN_PHONE_PATTERN.matcher(normalizedValue.replaceAll("\\s+", "")).matches()) {
                    throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Số điện thoại/Zalo không hợp lệ.");
                }
                break;
            case "EMAIL":
                if (!EMAIL_PATTERN.matcher(normalizedValue).matches()) {
                    throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Email không hợp lệ.");
                }
                break;
            default:
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
        return customerProfileRepository.findByUser_Id(user.getId())
                .orElseGet(() -> {
                    CustomerProfile profile = new CustomerProfile();
                    profile.setUser(user);
                    profile.setFullName(user.getEmail() != null ? user.getEmail() : "Khach hang");
                    return customerProfileRepository.save(profile);
                });
    }

    private Services validateService(Long serviceId) {
        if (serviceId == null || serviceId <= 0) {
            throw new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "Dịch vụ không tồn tại.");
        }
        Services service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "Dịch vụ không tồn tại."));

        if (!service.isActive()) {
            throw new BookingException(BookingErrorCode.SERVICE_INACTIVE, "Dịch vụ đang tạm ngưng.");
        }
        if (service.getDurationMinutes() <= 0) {
            throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "Thời lượng dịch vụ không hợp lệ.");
        }
        return service;
    }

    private void validateClientDeposit(Double clientDeposit, Services service) {
        double expectedDeposit = expectedDepositForService(service);
        if (clientDeposit == null) return;

        if (Math.abs(clientDeposit - expectedDeposit) > 0.0001d) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Tiền cọc không hợp lệ theo cấu hình dịch vụ.");
        }
    }

    private double expectedDepositForService(Services service) {
        return Math.max(0d, service.getPrice() * DEFAULT_DEPOSIT_RATE);
    }

    private LocalDateTime resolveStartDateTime(CreateAppointmentRequest request) {
        if (request.isOldFormat()) {
            Slot slot = bookingService.getSlotById(request.getSlotId())
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giờ không tồn tại."));
            return slot.getSlotTime();
        }
        return LocalDateTime.of(request.getSelectedDate(), request.getSelectedTime());
    }

    private void validateSelectedDate(LocalDate date) {
        LocalDate today = nowDateTime().toLocalDate();
        if (date == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ngày không hợp lệ.");
        }
        if (date.isBefore(today)) {
            throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "Không thể đặt lịch trong quá khứ.");
        }
    }

    private void validateNotInPast(LocalDateTime startDateTime) {
        LocalDateTime now = nowDateTime();
        if (!startDateTime.isAfter(now)) {
            throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "Không thể đặt lịch trong quá khứ.");
        }
    }

    private void validateWorkingWindow(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        LocalTime start = startDateTime.toLocalTime();
        LocalTime end = endDateTime.toLocalTime();

        if (endDateTime.toLocalDate().isAfter(startDateTime.toLocalDate())) {
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Thời gian nằm ngoài giờ làm việc.");
        }
        if (start.isBefore(CLINIC_OPEN) || !end.isAfter(start) || end.isAfter(CLINIC_CLOSE) || start.equals(CLINIC_CLOSE)) {
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
        boolean overlap = hasCustomerOverlap(userId, date, startTime, endTime, excludeAppointmentId);
        if (overlap) {
            throw new BookingException(BookingErrorCode.BOOKING_CONFLICT, "Bạn đã có lịch hẹn trùng thời điểm này.");
        }
    }

    private boolean hasCustomerOverlap(Long userId,
                                       LocalDate date,
                                       LocalTime startTime,
                                       LocalTime endTime,
                                       Long excludeAppointmentId) {
        return (excludeAppointmentId == null)
                ? appointmentRepository.existsCustomerOverlap(userId, date, startTime, endTime, ACTIVE_BOOKING_STATUSES)
                : appointmentRepository.existsCustomerOverlapExcludingAppointment(
                        userId, excludeAppointmentId, date, startTime, endTime, ACTIVE_BOOKING_STATUSES
                );
    }

    private void ensureRescheduleAllowed(Appointment appointment) {
        AppointmentStatus status = appointment.getStatus();
        if (status == AppointmentStatus.COMPLETED || status == AppointmentStatus.CANCELLED) {
            throw new BookingException(BookingErrorCode.RESCHEDULE_NOT_ALLOWED, "Không thể đổi lịch với trạng thái hiện tại.");
        }

        LocalDateTime appointmentStart = LocalDateTime.of(appointment.getDate(), appointment.getStartTime());
        if (!appointmentStart.isAfter(nowDateTime())) {
            throw new BookingException(BookingErrorCode.RESCHEDULE_NOT_ALLOWED, "Không thể đổi lịch sau giờ check-in.");
        }
    }

    private void ensureCancelAllowed(Appointment appointment) {
        AppointmentStatus status = appointment.getStatus();
        if (status == AppointmentStatus.COMPLETED || status == AppointmentStatus.CANCELLED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể hủy lịch với trạng thái hiện tại.");
        }

        LocalDateTime startDateTime = LocalDateTime.of(appointment.getDate(), appointment.getStartTime());
        LocalDateTime latestCancelTime = startDateTime.minusHours(CANCEL_MIN_HOURS_BEFORE);
        if (nowDateTime().isAfter(latestCancelTime)) {
            throw new BookingException(BookingErrorCode.CANCEL_WINDOW_CLOSED, "Không thể hủy lịch trong vòng 2 giờ trước giờ hẹn.");
        }
    }

    private LocalDateTime nowDateTime() {
        return LocalDateTime.now(CLINIC_ZONE);
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

        dto.setCanCheckIn(a.getDate().equals(nowDateTime().toLocalDate()) && a.getStatus() == AppointmentStatus.CONFIRMED);
        return dto;
    }
}
