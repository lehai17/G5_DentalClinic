package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.DentistScheduleRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.model.service.Services;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class CustomerAppointmentService {

    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final DentistScheduleRepository dentistScheduleRepository;
    private final ServiceRepository serviceRepository;

    // ===== Contact validation patterns =====
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern VN_PHONE_PATTERN =
            Pattern.compile("^(0|\\+84)\\d{9,10}$");

    public CustomerAppointmentService(CustomerProfileRepository customerProfileRepository,
                                      UserRepository userRepository,
                                      AppointmentRepository appointmentRepository,
                                      DentistScheduleRepository dentistScheduleRepository,
                                      ServiceRepository serviceRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.dentistScheduleRepository = dentistScheduleRepository;
        this.serviceRepository = serviceRepository;
    }

    /** Lấy hoặc tạo CustomerProfile cho user (user cũ có thể chưa có profile). */
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

    /** Validate request payload cơ bản + contact format. */
    private void validateCreateRequest(CreateAppointmentRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Dữ liệu đặt lịch không hợp lệ.");
        }
        if (request.getSlotId() == null || request.getSlotId() <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn khung giờ hợp lệ.");
        }
        if (request.getServiceId() == null || request.getServiceId() <= 0) {
            throw new IllegalArgumentException("Vui lòng chọn dịch vụ hợp lệ.");
        }

        // Note length
        if (request.getPatientNote() != null && request.getPatientNote().length() > 500) {
            throw new IllegalArgumentException("Ghi chú tối đa 500 ký tự.");
        }

        // Contact channel/value
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

    /** Validate rule nghiệp vụ của slot. */
    private void validateSlotBusinessRules(DentistSchedule slot) {
        if (slot == null) {
            throw new IllegalArgumentException("Khung giờ không tồn tại.");
        }
        if (!slot.isAvailable()) {
            throw new IllegalArgumentException("Khung giờ này hiện không khả dụng. Vui lòng chọn khung giờ khác.");
        }
        LocalDate today = LocalDate.now();
        if (slot.getDate() == null || slot.getDate().isBefore(today)) {
            throw new IllegalArgumentException("Không thể đặt lịch cho ngày trong quá khứ.");
        }
    }

    /**
     * Get available slots for a given date, optionally filtered by service and dentist.
     * serviceId and dentistId can be null (no filter).
     */
    public List<SlotDto> getAvailableSlots(Long serviceId, Long dentistId, LocalDate date) {
        List<DentistSchedule> schedules;
        if (dentistId != null) {
            schedules = dentistScheduleRepository.findByDentist_IdAndDate(dentistId, date);
        } else {
            schedules = dentistScheduleRepository.findByDate(date);
        }

        return schedules.stream()
                .filter(DentistSchedule::isAvailable)
                .filter(slot -> slot.getDentist() != null)
                .map(slot -> {
                    SlotDto dto = new SlotDto();
                    dto.setId(slot.getId());
                    dto.setDate(slot.getDate());
                    dto.setStartTime(slot.getStartTime());
                    dto.setEndTime(slot.getEndTime());
                    dto.setDentistId(slot.getDentist().getId());
                    dto.setDentistName(slot.getDentist().getFullName());

                    // Slot bận nếu đã có appointment còn hiệu lực (không tính CANCELLED)
                    boolean busy = appointmentRepository.existsBySlot_IdAndStatusNot(
                            slot.getId(),
                            AppointmentStatus.CANCELLED
                    );
                    dto.setAvailable(!busy);
                    return dto;
                })
                .filter(SlotDto::isAvailable)
                .collect(Collectors.toList());
    }

    @Transactional
    public AppointmentDto createAppointment(Long userId, CreateAppointmentRequest request) {
        // 1) validate request
        validateCreateRequest(request);

        // 2) customer profile
        CustomerProfile customer = getOrCreateCustomerProfile(userId);

        // 3) slot
        DentistSchedule slot = dentistScheduleRepository.findById(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Khung giờ không tồn tại."));
        validateSlotBusinessRules(slot);

        // 4) service
        Services service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Dịch vụ không tồn tại."));

        // 5) không cho trùng slot (không tính CANCELLED)
        if (appointmentRepository.existsBySlot_IdAndStatusNot(slot.getId(), AppointmentStatus.CANCELLED)) {
            throw new IllegalArgumentException("Khung giờ này đã được đặt. Vui lòng chọn khung giờ khác.");
        }

        // 6) create
        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setSlot(slot);

        // Staff sẽ gán bác sĩ sau
        appointment.setService(service);
        appointment.setDate(slot.getDate());
        appointment.setStartTime(slot.getStartTime());
        appointment.setEndTime(slot.getEndTime());
        appointment.setStatus(AppointmentStatus.PENDING);

        // normalize text
        appointment.setNotes(request.getPatientNote() != null ? request.getPatientNote().trim() : null);
        appointment.setContactChannel(request.getContactChannel().trim().toUpperCase());
        appointment.setContactValue(request.getContactValue().trim());

        appointment = appointmentRepository.save(appointment);
        return toDto(appointment);
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

    @Transactional
    public Optional<AppointmentDto> cancelAppointment(Long userId, Long appointmentId) {
        Optional<Appointment> opt = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId);
        if (opt.isEmpty()) return Optional.empty();

        Appointment a = opt.get();

        if (a.getStatus() == AppointmentStatus.COMPLETED || a.getStatus() == AppointmentStatus.CANCELLED) {
            return Optional.empty();
        }

        a.setStatus(AppointmentStatus.CANCELLED);
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

        LocalDate today = LocalDate.now();
        dto.setCanCheckIn(a.getDate().equals(today) && a.getStatus() == AppointmentStatus.CONFIRMED);

        return dto;
    }
}
