package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.AppointmentInvoiceItemDto;
import com.dentalclinic.dto.customer.AppointmentPrescriptionItemDto;
import com.dentalclinic.dto.customer.AppointmentServiceItemDto;
import com.dentalclinic.dto.customer.CreateReviewRequest;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.RebookPrefillDto;
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
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.model.payment.BillingPerformedService;
import com.dentalclinic.model.payment.BillingPrescriptionItem;
import com.dentalclinic.model.payment.PaymentStatus;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.model.review.Review;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.InvoiceRepository;
import com.dentalclinic.repository.ReviewRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.SlotRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import com.dentalclinic.service.notification.NotificationService;
import com.dentalclinic.service.wallet.WalletService;
import com.dentalclinic.model.wallet.WalletTransactionType;
import jakarta.persistence.EntityManager;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Locale;
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
    private final InvoiceRepository invoiceRepository;
    private final ReviewRepository reviewRepository;
    private final ServiceRepository serviceRepository;
    private final SlotRepository slotRepository;
    private final NotificationService notificationService;
    private final WalletService walletService;
    private final WalletTransactionRepository walletTransactionRepository;
    private final EntityManager entityManager;

    public CustomerAppointmentService(CustomerProfileRepository customerProfileRepository,
                                      UserRepository userRepository,
                                      AppointmentRepository appointmentRepository,
                                      BillingNoteRepository billingNoteRepository,
                                      InvoiceRepository invoiceRepository,
                                      ReviewRepository reviewRepository,
                                      ServiceRepository serviceRepository,
                                      SlotRepository slotRepository,
                                      NotificationService notificationService,
                                      WalletService walletService,
                                      WalletTransactionRepository walletTransactionRepository,
                                      EntityManager entityManager) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.billingNoteRepository = billingNoteRepository;
        this.invoiceRepository = invoiceRepository;
        this.reviewRepository = reviewRepository;
        this.serviceRepository = serviceRepository;
        this.slotRepository = slotRepository;
        this.notificationService = notificationService;
        this.walletService = walletService;
        this.walletTransactionRepository = walletTransactionRepository;
        this.entityManager = entityManager;
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
            LocalDateTime end = calculateAppointmentEndDateTime(s.getSlotTime(), slotsNeeded);
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
        LocalDateTime end = calculateAppointmentEndDateTime(start, slotsNeeded);

        validateNotInPast(start);
        validateWorkingWindow(start, end);
        validateNoCustomerOverlap(user.getId(), start.toLocalDate(), start.toLocalTime(), end.toLocalTime(), null);
        validateNoDuplicateServiceInSameDay(user.getId(), start.toLocalDate(), requestedServiceIds, null);

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
        LocalDateTime newEnd = calculateAppointmentEndDateTime(newStart, slotsNeeded);

        validateNotInPast(newStart);
        validateWorkingWindow(newStart, newEnd);
        validateNoCustomerOverlap(userId, newStart.toLocalDate(), newStart.toLocalTime(), newEnd.toLocalTime(), appointment.getId());
        validateNoDuplicateServiceInSameDay(userId, newStart.toLocalDate(), resolveAppointmentServiceIds(appointment), appointment.getId());

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
        Appointment cancelled = cancelAppointmentEntity(
                appointmentId,
                null,
                isCustomerDepositRefundEligible(appointment)
        );
        notificationService.notifyBookingCancelled(cancelled, "Khách hàng đã hủy lịch hẹn");
        return toDto(cancelled);
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

        if (appointment.getStatus() != AppointmentStatus.PENDING_DEPOSIT) {
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

        if (appointment.getStatus() != AppointmentStatus.PENDING_DEPOSIT) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể xác nhận thanh toán cho lịch hẹn này.");
        }

        appointment.setStatus(AppointmentStatus.PENDING);
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.notifyBookingCreated(saved);
        return saved;
    }

    @Transactional
    public Appointment payDepositWithWallet(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithSlotsAndCustomerUserId(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() != AppointmentStatus.PENDING_DEPOSIT) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Lịch hẹn này không ở trạng thái chờ thanh toán cọc.");
        }

        BigDecimal depositAmount = resolveAppointmentDepositAmount(appointment);
        if (depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Số tiền đặt cọc không hợp lệ.");
        }

        walletService.pay(
                appointment.getCustomer(),
                depositAmount,
                "Thanh to?n ti?n c?c l?ch h?n #" + appointment.getId(),
                appointment.getId()
        );

        appointment.setStatus(AppointmentStatus.PENDING);
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.notifyBookingCreated(saved);
        return saved;
    }

    @Transactional
    public Appointment payRemainingWithWallet(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithSlotsAndCustomerUserId(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() != AppointmentStatus.WAITING_PAYMENT) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Lịch hẹn này không ở trạng thái chờ thanh toán phần còn lại.");
        }

        Invoice invoice = invoiceRepository.findByAppointment_Id(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy hóa đơn thanh toán."));

        if (invoice.getStatus() != null && "PAID".equals(invoice.getStatus().name())) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Hóa đơn này đã được thanh toán.");
        }
        BigDecimal remainingAmount = resolveRemainingInvoiceAmount(appointment, invoice);
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Số tiền thanh toán không hợp lệ.");
        }

        invoice.setTotalAmount(remainingAmount);
        invoiceRepository.save(invoice);

        walletService.pay(
                appointment.getCustomer(),
                remainingAmount,
                "Thanh toán hóa đơn còn lại lịch hẹn #" + appointment.getId(),
                appointment.getId()
        );

        invoice.setStatus(com.dentalclinic.model.payment.PaymentStatus.PAID);
        invoiceRepository.save(invoice);

        appointment.setStatus(AppointmentStatus.COMPLETED);
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.notifyBookingUpdated(saved, "đã thanh toán hóa đơn thành công");
        return saved;
    }

    public List<AppointmentDto> getMyAppointments(Long userId) {
        return getMyAppointments(userId, "default");
    }

    public List<AppointmentDto> getMyAppointments(Long userId, String view) {
        return findAppointmentsForView(userId, view).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RebookPrefillDto prepareRebookPrefill(Long userId, Long appointmentId) {
        validateBookingUser(userId);

        Appointment appointment = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy lịch hẹn để đặt lại."));

        if (!isRebookEligibleStatus(appointment.getStatus())) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Chỉ có thể đặt lại từ lịch hẹn đã hoàn thành hoặc đã hủy.");
        }

        RebookPrefillDto dto = new RebookPrefillDto();
        dto.setSourceAppointmentId(appointment.getId());
        dto.setPatientNote(appointment.getNotes());
        dto.setContactChannel(appointment.getContactChannel());
        dto.setContactValue(appointment.getContactValue());

        List<Long> oldServiceIds = resolveAppointmentServiceIds(appointment);
        List<Long> activeServiceIds = serviceRepository.findAllById(oldServiceIds).stream()
                .filter(Services::isActive)
                .map(Services::getId)
                .collect(Collectors.toList());
        dto.setServiceIds(activeServiceIds);

        List<String> unavailableServiceNames = new ArrayList<>();
        if (!oldServiceIds.isEmpty()) {
            for (AppointmentServiceItemDto item : toDto(appointment).getServices()) {
                if (item.getServiceId() == null || activeServiceIds.contains(item.getServiceId())) {
                    continue;
                }
                if (item.getServiceName() != null && !item.getServiceName().isBlank()) {
                    unavailableServiceNames.add(item.getServiceName());
                }
            }
        }

        if (!unavailableServiceNames.isEmpty()) {
            dto.setWarningMessage("Một số dịch vụ từ lịch hẹn cũ không còn áp dụng: " + String.join(", ", unavailableServiceNames) + ".");
        } else if (!oldServiceIds.isEmpty() && activeServiceIds.isEmpty()) {
            dto.setWarningMessage("Dịch vụ từ lịch hẹn cũ hiện không còn khả dụng. Vui lòng chọn dịch vụ mới trước khi tiếp tục.");
        }

        if (appointment.getDentist() != null && appointment.getDentist().getUser() != null
                && appointment.getDentist().getUser().getStatus() == UserStatus.ACTIVE) {
            dto.setDentistId(appointment.getDentist().getId());
        }

        return dto;
    }

    public Page<AppointmentDto> getMyAppointmentsPage(Long userId, int page, int size) {
        return getMyAppointmentsPage(userId, page, size, null, "date_desc", "default");
    }

    public Page<AppointmentDto> getMyAppointmentsPage(Long userId, int page, int size, String keyword) {
        return getMyAppointmentsPage(userId, page, size, keyword, "date_desc", "default");
    }

    public Page<AppointmentDto> getMyAppointmentsPage(Long userId, int page, int size, String keyword, String sort) {
        return getMyAppointmentsPage(userId, page, size, keyword, sort, "default");
    }

    public Page<AppointmentDto> getMyAppointmentsPage(Long userId, int page, int size, String keyword, String sort, String view) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        String normalizedSort = normalizeAppointmentSort(sort);
        String normalizedView = normalizeAppointmentView(view);

        List<AppointmentDto> allDtos = findAppointmentsForView(userId, normalizedView).stream()
                .sorted(resolveAppointmentComparator(normalizedSort))
                .map(this::toDto)
                .filter(dto -> matchesAppointmentKeyword(dto, keyword))
                .collect(Collectors.toList());

        int fromIndex = Math.min(safePage * safeSize, allDtos.size());
        int toIndex = Math.min(fromIndex + safeSize, allDtos.size());

        return new PageImpl<>(
                allDtos.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.desc("date"), Sort.Order.desc("startTime"))),
                allDtos.size()
        );
    }

    public String normalizeAppointmentSort(String sort) {
        String normalized = sort == null ? "" : sort.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "date_asc", "date_desc", "booked_asc", "booked_desc", "status_asc", "status_desc" -> normalized;
            case "created_asc" -> "booked_asc";
            case "created_desc" -> "booked_desc";
            default -> "date_desc";
        };
    }

    public String normalizeAppointmentView(String view) {
        String normalized = view == null ? "" : view.trim().toLowerCase(Locale.ROOT);
        return "cancelled".equals(normalized) ? "cancelled" : "default";
    }

    private Comparator<Appointment> resolveAppointmentComparator(String sort) {
        String normalized = normalizeAppointmentSort(sort);

        Comparator<Appointment> byDateAsc = Comparator
                .comparing(Appointment::getDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(Appointment::getStartTime, Comparator.nullsLast(LocalTime::compareTo))
                .thenComparing(Appointment::getId, Comparator.nullsLast(Long::compareTo));

        Comparator<Appointment> byDateDesc = Comparator
                .comparing(Appointment::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Appointment::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Appointment::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        Comparator<Appointment> byCreatedAsc = Comparator
                .comparing(Appointment::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .thenComparing(Appointment::getDate, Comparator.nullsLast(LocalDate::compareTo))
                .thenComparing(Appointment::getStartTime, Comparator.nullsLast(LocalTime::compareTo))
                .thenComparing(Appointment::getId, Comparator.nullsLast(Long::compareTo));

        Comparator<Appointment> byCreatedDesc = Comparator
                .comparing(Appointment::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Appointment::getId, Comparator.nullsLast(Comparator.reverseOrder()));

        Comparator<Appointment> byStatusAsc = Comparator
                .comparing((Appointment appointment) -> appointment.getStatus() == null ? "" : appointment.getStatus().name())
                .thenComparing(Appointment::getDate, Comparator.nullsLast(LocalDate::compareTo).reversed())
                .thenComparing(Appointment::getStartTime, Comparator.nullsLast(LocalTime::compareTo).reversed())
                .thenComparing(Appointment::getId, Comparator.nullsLast(Long::compareTo).reversed());

        Comparator<Appointment> byStatusDesc = Comparator
                .comparing((Appointment appointment) -> appointment.getStatus() == null ? "" : appointment.getStatus().name(), Comparator.reverseOrder())
                .thenComparing(Appointment::getDate, Comparator.nullsLast(LocalDate::compareTo).reversed())
                .thenComparing(Appointment::getStartTime, Comparator.nullsLast(LocalTime::compareTo).reversed())
                .thenComparing(Appointment::getId, Comparator.nullsLast(Long::compareTo).reversed());

        return switch (normalized) {
            case "date_asc" -> byDateAsc;
            case "booked_desc" -> byCreatedDesc;
            case "booked_asc" -> byCreatedAsc;
            case "status_asc" -> byStatusAsc;
            case "status_desc" -> byStatusDesc;
            default -> byDateDesc;
        };
    }

    private boolean matchesAppointmentKeyword(AppointmentDto dto, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return true;
        }

        return containsKeyword(String.valueOf(dto.getId()), normalizedKeyword)
                || containsKeyword(dto.getServiceName(), normalizedKeyword)
                || containsKeyword(dto.getDentistName(), normalizedKeyword)
                || containsKeyword(dto.getStatus(), normalizedKeyword)
                || containsKeyword(dto.getContactChannel(), normalizedKeyword)
                || containsKeyword(dto.getContactValue(), normalizedKeyword)
                || containsKeyword(dto.getNotes(), normalizedKeyword)
                || containsKeyword(formatAppointmentDate(dto.getDate()), normalizedKeyword);
    }

    private String formatAppointmentDate(LocalDate date) {
        return date == null ? "" : String.format("%02d/%02d/%04d", date.getDayOfMonth(), date.getMonthValue(), date.getYear());
    }

    private boolean containsKeyword(String value, String normalizedKeyword) {
        return value != null && normalizeKeyword(value).contains(normalizedKeyword);
    }

    private String normalizeKeyword(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public Optional<AppointmentDto> getAppointmentDetail(Long userId, Long appointmentId) {
        return appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId).map(this::toDto);
    }

    @Transactional
    public Optional<AppointmentDto> checkIn(Long userId, Long appointmentId) {
        validateBookingUser(userId);
        throw new BookingException(
                BookingErrorCode.APPOINTMENT_STATUS_INVALID,
                "Lịch hẹn không còn dùng bước check-in. Sau khi lễ tân xác nhận, bác sĩ sẽ bắt đầu khám trực tiếp."
        );
    }

    @Transactional
    public void hideCancelledAppointmentFromHistory(Long userId, Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy lịch hẹn."));

        if (appointment.getStatus() != AppointmentStatus.CANCELLED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Chỉ có thể ẩn khỏi lịch sử với lịch hẹn đã hủy.");
        }

        appointment.setCustomerHidden(true);
        appointmentRepository.save(appointment);
    }

    @Transactional
    public Review createReview(Long userId, Long appointmentId, CreateReviewRequest request) {
        validateBookingUser(userId);
        if (request == null || request.getRating() == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Vui lòng chọn số sao đánh giá.");
        }

        Appointment appointment = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Không tìm thấy lịch hẹn để đánh giá."));

        if (!(appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.DONE)) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Chỉ có thể đánh giá sau khi lịch hẹn đã hoàn thành.");
        }

        if (appointment.getDentist() == null) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Lịch hẹn này chưa có thông tin bác sĩ để đánh giá.");
        }

        if (reviewRepository.existsByAppointment_Id(appointmentId)) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Lịch hẹn này đã được đánh giá trước đó.");
        }

        int rating = request.getRating();
        if (rating < 1 || rating > 5) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Số sao đánh giá phải từ 1 đến 5.");
        }

        String comment = request.getComment() == null ? null : request.getComment().trim();
        if (comment != null && comment.isEmpty()) {
            comment = null;
        }
        if (comment != null && comment.length() > 500) {
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Nội dung đánh giá tối đa 500 ký tự.");
        }

        Review review = new Review();
        review.setAppointment(appointment);
        review.setCustomer(appointment.getCustomer());
        review.setDentist(appointment.getDentist());
        review.setRating(rating);
        review.setComment(comment);
        return reviewRepository.save(review);
    }

    private List<Appointment> findAppointmentsForView(Long userId, String view) {
        String normalizedView = normalizeAppointmentView(view);
        if ("cancelled".equals(normalizedView)) {
            return appointmentRepository.findByCustomer_User_IdAndStatusOrderByDateDescStartTimeDesc(
                    userId,
                    AppointmentStatus.CANCELLED
            );
        }

        return appointmentRepository.findByCustomer_User_IdAndCustomerHiddenFalseOrderByDateDesc(userId).stream()
                .filter(appointment -> appointment.getStatus() != AppointmentStatus.PENDING_DEPOSIT)
                .filter(appointment -> appointment.getStatus() != AppointmentStatus.CANCELLED)
                .collect(Collectors.toList());
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
                ? slotRepository.findAllSlotsForToday(findNextSlotStart(now), dayEnd)
                : slotRepository.findAllSlotsBetweenTimes(dayStart, dayEnd);
        Map<LocalDateTime, Slot> slotByTime = all.stream()
                .collect(Collectors.toMap(Slot::getSlotTime, slot -> slot, (left, right) -> left));

        List<Slot> availableStarts = new ArrayList<>();
        for (Slot candidate : all) {
            List<LocalDateTime> requiredSlotTimes = buildRequiredSlotTimes(candidate.getSlotTime(), slotsNeeded);
            if (requiredSlotTimes.size() != slotsNeeded) {
                continue;
            }
            boolean good = true;
            for (LocalDateTime requiredSlotTime : requiredSlotTimes) {
                Slot slot = slotByTime.get(requiredSlotTime);
                if (slot == null || !slot.isAvailable()) {
                    good = false;
                    break;
                }
            }
            if (good) {
                availableStarts.add(candidate);
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

        LocalDateTime endDateTime = calculateAppointmentEndDateTime(startDateTime, slotsNeeded);
        if (!isInsideWorkingWindow(startDateTime, endDateTime)) {
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Th\u1eddi gian n\u1eb1m ngo\u00e0i gi\u1edd l\u00e0m vi\u1ec7c.");
        }

        List<LocalDateTime> requiredSlotTimes = buildRequiredSlotTimes(startDateTime, slotsNeeded);
        if (requiredSlotTimes.size() != slotsNeeded) {
            return new ArrayList<>();
        }

        List<Slot> locked = new ArrayList<>();
        for (LocalDateTime requiredSlotTime : requiredSlotTimes) {
            Slot slot = slotRepository.findBySlotTimeAndActiveTrueForUpdate(requiredSlotTime).orElse(null);
            if (slot == null || !slot.isAvailable()) {
                return new ArrayList<>();
            }
            locked.add(slot);
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
            Slot locked = slotRepository.findBySlotTimeForUpdateRegardlessActive(slot.getSlotTime())
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung gi\u1edd kh\u00f4ng t\u1ed3n t\u1ea1i."));

            if (locked.getBookedCount() > 0) {
                locked.setBookedCount(locked.getBookedCount() - 1);
            }

            if (!locked.isActive()) {
                locked.setActive(true);
            }
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

        appointment.setStatus(AppointmentStatus.PENDING_DEPOSIT);
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
        return appointmentRepository.findByCustomer_IdAndDateAndStatusOrderByCreatedAtDesc(
                        customer.getId(),
                        date,
                        AppointmentStatus.CANCELLED
                ).stream()
                .filter(appointment -> startTime.equals(appointment.getStartTime()))
                .filter(appointment -> billingNoteRepository.findByAppointment_Id(appointment.getId()).isEmpty())
                .map(appointment -> resetCancelledAppointmentForReuse(appointment, customer))
                .findFirst()
                .orElseGet(() -> {
                    Appointment appointment = new Appointment();
                    appointment.setCustomer(customer);
                    return appointment;
                });
    }

    private Appointment resetCancelledAppointmentForReuse(Appointment appointment, CustomerProfile customer) {
        // Avoid fetching two List collections in one query. Initialize lazily inside the transaction instead.
        appointment.getAppointmentSlots().size();
        appointment.getAppointmentDetails().size();
        appointment.clearAppointmentSlots();
        appointment.clearAppointmentDetails();
        entityManager.flush();
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
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Kh\u00f4ng th\u1ec3 x\u00e1c nh\u1eadn l\u1ecbch h\u1eb9n \u1edf tr\u1ea1ng th\u00e1i hi\u1ec7n t\u1ea1i.");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    private Appointment cancelAppointmentEntity(Long appointmentId, String reason, boolean refundIfEligible) {
        Appointment appointment = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.DONE
                || appointment.getStatus() == AppointmentStatus.EXAMINING
                || appointment.getStatus() == AppointmentStatus.IN_PROGRESS) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể hủy lịch với trạng thái hiện tại.");
        }

        if (refundIfEligible && (appointment.getStatus() == AppointmentStatus.PENDING || appointment.getStatus() == AppointmentStatus.CONFIRMED)) {
            BigDecimal refundAmount = resolveAppointmentDepositAmount(appointment);
            if (refundAmount.compareTo(BigDecimal.ZERO) > 0) {
                walletService.refund(
                        appointment.getCustomer(),
                        refundAmount,
                        "Ho?n ti?n ??t c?c l?ch h?n #" + appointment.getId(),
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

        // Dentist inbox notification: appointment cancelled (if assigned)
        notificationService.notifyDentistAppointmentCancelled(saved, reason);

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
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ghi ch\u00fa t\u1ed1i \u0111a 500 k\u00fd t\u1ef1.");
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
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Email kh\u00f4ng h\u1ee3p l\u1ec7.");
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
            throw new BookingException(BookingErrorCode.SERVICE_NOT_FOUND, "Danh s\u00e1ch d\u1ecbch v\u1ee5 kh\u00f4ng h\u1ee3p l\u1ec7.");
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
            throw new BookingException(BookingErrorCode.VALIDATION_ERROR, "Ti?n c?c kh?ng h?p l? theo c?u h?nh booking.");
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

    private List<Long> resolveAppointmentServiceIds(Appointment appointment) {
        List<Long> ids = new ArrayList<>();
        if (appointment.getAppointmentDetails() != null && !appointment.getAppointmentDetails().isEmpty()) {
            for (AppointmentDetail detail : appointment.getAppointmentDetails()) {
                if (detail.getService() != null && detail.getService().getId() != null) {
                    ids.add(detail.getService().getId());
                }
            }
            return ids;
        }

        if (appointment.getService() != null && appointment.getService().getId() != null) {
            ids.add(appointment.getService().getId());
        }
        return ids;
    }

    private LocalDateTime resolveStartDateTime(CreateAppointmentRequest request) {
        if (request.isOldFormat()) {
            Slot slot = slotRepository.findById(request.getSlotId())
                    .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung gi\u1edd kh\u00f4ng t\u1ed3n t\u1ea1i."));
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
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Th\u1eddi gian n\u1eb1m ngo\u00e0i gi\u1edd l\u00e0m vi\u1ec7c.");
        }

        if (start.isBefore(OPEN) || !end.isAfter(start) || end.isAfter(CLOSE) || start.equals(CLOSE)) {
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Th\u1eddi gian n\u1eb1m ngo\u00e0i gi\u1edd l\u00e0m vi\u1ec7c.");
        }

        if (isDuringLunchBreak(start)) {
            throw new BookingException(BookingErrorCode.LUNCH_BREAK_CONFLICT, "Không thể bắt đầu lịch hẹn trong giờ nghỉ trưa 12:00-13:00.");
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

    private void validateNoDuplicateServiceInSameDay(Long userId,
                                                     LocalDate date,
                                                     List<Long> serviceIds,
                                                     Long excludeAppointmentId) {
        if (serviceIds == null || serviceIds.isEmpty()) {
            return;
        }

        HashSet<Long> requestedServiceIds = new HashSet<>(serviceIds);
        List<Appointment> sameDayAppointments = appointmentRepository
                .findByCustomer_User_IdAndDateAndStatusNotOrderByStartTimeAsc(
                        userId,
                        date,
                        AppointmentStatus.CANCELLED
                );

        for (Appointment appointment : sameDayAppointments) {
            if (excludeAppointmentId != null && excludeAppointmentId.equals(appointment.getId())) {
                continue;
            }

            for (Long existingServiceId : resolveAppointmentServiceIds(appointment)) {
                if (requestedServiceIds.contains(existingServiceId)) {
                    throw new BookingException(
                            BookingErrorCode.BOOKING_CONFLICT,
                            "Không thể đặt trùng dịch vụ trong cùng một ngày."
                    );
                }
            }
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

    private boolean isRebookEligibleStatus(AppointmentStatus status) {
        return status == AppointmentStatus.COMPLETED
                || status == AppointmentStatus.DONE
                || status == AppointmentStatus.CANCELLED
                || status == AppointmentStatus.REEXAM;
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
        if (appointment.getStatus() == AppointmentStatus.COMPLETED
                || appointment.getStatus() == AppointmentStatus.CANCELLED
                || appointment.getStatus() == AppointmentStatus.EXAMINING
                || appointment.getStatus() == AppointmentStatus.IN_PROGRESS) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể hủy lịch với trạng thái hiện tại.");
        }
    }

    private boolean isCustomerDepositRefundEligible(Appointment appointment) {
        if (appointment == null || appointment.getDate() == null) {
            return false;
        }

        AppointmentStatus status = appointment.getStatus();
        if (!(status == AppointmentStatus.PENDING || status == AppointmentStatus.CONFIRMED)) {
            return false;
        }

        return nowDateTime().toLocalDate().isBefore(appointment.getDate());
    }

    private LocalDateTime findNextSlotStart(LocalDateTime now) {
        int slotMinute = (now.getMinute() / SLOT_MIN) * SLOT_MIN;
        LocalDateTime currentSlotStart = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), slotMinute));
        LocalDateTime candidate = now.isAfter(currentSlotStart) ? currentSlotStart.plusMinutes(SLOT_MIN) : currentSlotStart;
        if (isDuringLunchBreak(candidate.toLocalTime())) {
            return LocalDateTime.of(candidate.toLocalDate(), LUNCH_END);
        }
        return candidate;
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

        return !isDuringLunchBreak(start);
    }

    private LocalDateTime calculateAppointmentEndDateTime(LocalDateTime startDateTime, int slotsNeeded) {
        List<LocalDateTime> requiredSlotTimes = buildRequiredSlotTimes(startDateTime, slotsNeeded);
        if (requiredSlotTimes.size() != slotsNeeded) {
            return startDateTime;
        }

        return requiredSlotTimes.get(requiredSlotTimes.size() - 1).plusMinutes(SLOT_MIN);
    }

    private List<LocalDateTime> buildRequiredSlotTimes(LocalDateTime startDateTime, int slotsNeeded) {
        List<LocalDateTime> requiredSlotTimes = new ArrayList<>();
        if (slotsNeeded <= 0 || startDateTime == null) {
            return requiredSlotTimes;
        }

        LocalDateTime current = startDateTime;
        for (int i = 0; i < slotsNeeded; i++) {
            if (!isValidWorkingSlotStart(current)) {
                return new ArrayList<>();
            }

            requiredSlotTimes.add(current);
            current = advanceToNextWorkingSlot(current);
        }
        return requiredSlotTimes;
    }

    private LocalDateTime advanceToNextWorkingSlot(LocalDateTime slotStart) {
        LocalDateTime next = slotStart.plusMinutes(SLOT_MIN);
        if (next.toLocalTime().equals(LUNCH_START) || isDuringLunchBreak(next.toLocalTime())) {
            return LocalDateTime.of(next.toLocalDate(), LUNCH_END);
        }
        return next;
    }

    private boolean isValidWorkingSlotStart(LocalDateTime slotStart) {
        if (slotStart == null) {
            return false;
        }

        LocalTime time = slotStart.toLocalTime();
        return !time.isBefore(OPEN)
                && time.isBefore(CLOSE)
                && !isDuringLunchBreak(time);
    }

    private boolean isDuringLunchBreak(LocalTime time) {
        return !time.isBefore(LUNCH_START) && time.isBefore(LUNCH_END);
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
        dto.setCreatedAt(appointment.getCreatedAt());
        dto.setStatus(appointment.getStatus().name());
        dto.setNotes(appointment.getNotes());
        dto.setContactChannel(appointment.getContactChannel());
        dto.setContactValue(appointment.getContactValue());
        dto.setCancellationReason(appointment.getStatus() == AppointmentStatus.CANCELLED ? appointment.getNotes() : null);
        dto.setDepositRefunded(
                appointment.getStatus() == AppointmentStatus.CANCELLED
                        && walletTransactionRepository.existsByAppointmentIdAndType(
                        appointment.getId(),
                        WalletTransactionType.REFUND
                )
        );

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

        billingNoteRepository.findByAppointment_Id(appointment.getId()).ifPresent(billingNote -> {
            List<AppointmentInvoiceItemDto> invoiceItems = new ArrayList<>();
            List<AppointmentPrescriptionItemDto> prescriptionItems = new ArrayList<>();
            BigDecimal billedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            dto.setBillingNoteId(billingNote.getId());
            dto.setBillingNoteNote(billingNote.getNote());
            dto.setBillingNoteUpdatedAt(billingNote.getUpdatedAt());

            if (billingNote.getPerformedServices() != null) {
                for (BillingPerformedService item : billingNote.getPerformedServices()) {
                    if (item.getService() == null) {
                        continue;
                    }
                    int qty = Math.max(1, item.getQty());
                    BigDecimal unitPrice = BigDecimal.valueOf(item.getService().getPrice()).setScale(2, RoundingMode.HALF_UP);
                    BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);

                    AppointmentInvoiceItemDto line = new AppointmentInvoiceItemDto();
                    line.setId(item.getId());
                    line.setServiceId(item.getService().getId());
                    line.setName(item.getService().getName());
                    line.setQty(qty);
                    line.setUnitPrice(unitPrice);
                    line.setAmount(lineAmount);
                    invoiceItems.add(line);

                    billedTotal = billedTotal.add(lineAmount);
                }
            }

            if (billingNote.getPrescriptionItems() != null) {
                for (BillingPrescriptionItem item : billingNote.getPrescriptionItems()) {
                    if (item == null) {
                        continue;
                    }
                    AppointmentPrescriptionItemDto line = new AppointmentPrescriptionItemDto();
                    line.setId(item.getId());
                    line.setMedicineName(item.getMedicineName());
                    line.setDosage(item.getDosage());
                    line.setNote(item.getNote());
                    prescriptionItems.add(line);
                }
            }

            dto.setInvoiceItems(invoiceItems);
            dto.setPrescriptionItems(prescriptionItems);
            dto.setBilledTotal(billedTotal);
        });

        invoiceRepository.findByAppointment_Id(appointment.getId()).ifPresent(invoice -> {
            BigDecimal remainingAmount = resolveRemainingInvoiceAmount(appointment, invoice);
            dto.setInvoiceId(invoice.getId());
            dto.setInvoiceStatus(invoice.getStatus().name());
            dto.setRemainingAmount(remainingAmount);
            dto.setCanPayRemaining(
                    appointment.getStatus() == AppointmentStatus.WAITING_PAYMENT
                            && invoice.getStatus() != null
                            && "UNPAID".equals(invoice.getStatus().name())
                            && remainingAmount.compareTo(BigDecimal.ZERO) > 0
            );
        });

        if (appointment.getDentist() != null) {
            dto.setDentistId(appointment.getDentist().getId());
            dto.setDentistName(appointment.getDentist().getFullName());
        }

        reviewRepository.findByAppointment_Id(appointment.getId()).ifPresent(review -> {
            dto.setReviewed(true);
            dto.setReviewRating(review.getRating());
            dto.setReviewComment(review.getComment());
            dto.setReviewCreatedAt(review.getCreatedAt());
        });

        dto.setCanCheckIn(false);
        dto.setCanRebook(isRebookEligibleStatus(appointment.getStatus()));
        dto.setCanReview(
                !dto.isReviewed()
                        && appointment.getDentist() != null
                        && (appointment.getStatus() == AppointmentStatus.COMPLETED || appointment.getStatus() == AppointmentStatus.DONE)
        );
        return dto;
    }

    public BigDecimal resolveRemainingInvoiceAmount(Appointment appointment, Invoice invoice) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal invoiceAmount = invoice != null && invoice.getTotalAmount() != null
                ? invoice.getTotalAmount().setScale(2, RoundingMode.HALF_UP)
                : zero;

        return billingNoteRepository.findByAppointment_Id(appointment.getId())
                .map(billingNote -> {
                    BigDecimal billedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                    if (billingNote.getPerformedServices() != null) {
                        for (BillingPerformedService item : billingNote.getPerformedServices()) {
                            if (item.getService() == null) {
                                continue;
                            }
                            int qty = Math.max(1, item.getQty());
                            BigDecimal unitPrice = BigDecimal.valueOf(item.getService().getPrice()).setScale(2, RoundingMode.HALF_UP);
                            billedTotal = billedTotal.add(unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP));
                        }
                    }

                    BigDecimal depositAmount = resolveAppointmentDepositAmount(appointment);
                    if (depositAmount == null) {
                        depositAmount = zero;
                    }

                    return billedTotal.subtract(depositAmount)
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP);
                })
                .orElse(invoiceAmount);
    }

    private record BookingTotals(int totalDurationMinutes, BigDecimal totalAmount, BigDecimal depositAmount) {
    }
}
