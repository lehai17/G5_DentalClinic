package com.dentalclinic.service.booking;

import com.dentalclinic.exception.BookingErrorCode;
import com.dentalclinic.exception.BookingException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentSlot;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final LocalTime CLINIC_OPEN_TIME = LocalTime.of(8, 0);
    private static final LocalTime LUNCH_START_TIME = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END_TIME = LocalTime.of(13, 0);
    private static final LocalTime CLINIC_CLOSE_TIME = LocalTime.of(17, 0);
    private static final int SLOT_DURATION_MINUTES = 30;

    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;

    public BookingService(SlotRepository slotRepository,
                          AppointmentRepository appointmentRepository) {
        this.slotRepository = slotRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public static int calculateSlotsNeeded(int durationMinutes) {
        return (int) Math.ceil((double) durationMinutes / SLOT_DURATION_MINUTES);
    }

    public List<Slot> getAvailableSlotsForService(LocalDate date, int durationMinutes) {
        int slotsNeeded = calculateSlotsNeeded(durationMinutes);
        LocalDateTime now = nowDateTime();
        LocalDateTime dayStart = LocalDateTime.of(date, CLINIC_OPEN_TIME);
        LocalDateTime dayEnd = LocalDateTime.of(date, CLINIC_CLOSE_TIME);

        if (date.isBefore(now.toLocalDate())) return new ArrayList<>();
        if (dayEnd.isBefore(now)) return new ArrayList<>();

        List<Slot> allSlots = date.equals(now.toLocalDate())
                ? slotRepository.findAvailableSlotsForToday(findNextSlotStart(now), dayEnd)
                : slotRepository.findAvailableSlotsBetweenTimes(dayStart, dayEnd);

        List<Slot> availableSlots = new ArrayList<>();
        for (int i = 0; i <= allSlots.size() - slotsNeeded; i++) {
            boolean consecutive = true;
            for (int j = 0; j < slotsNeeded; j++) {
                Slot slot = allSlots.get(i + j);
                LocalDateTime expected = allSlots.get(i).getSlotTime().plusMinutes((long) j * SLOT_DURATION_MINUTES);
                if (!slot.getSlotTime().equals(expected) || !slot.isAvailable()) {
                    consecutive = false;
                    break;
                }
            }
            if (consecutive) {
                LocalDateTime start = allSlots.get(i).getSlotTime();
                LocalDateTime end = start.plusMinutes((long) slotsNeeded * SLOT_DURATION_MINUTES);
                if (isInsideWorkingWindow(start, end)) {
                    availableSlots.add(allSlots.get(i));
                }
            }
        }
        return availableSlots;
    }

    public List<Slot> getAllSlotsForDate(LocalDate date) {
        LocalDateTime now = nowDateTime();
        LocalDateTime dayStart = LocalDateTime.of(date, CLINIC_OPEN_TIME);
        LocalDateTime dayEnd = LocalDateTime.of(date, CLINIC_CLOSE_TIME);

        if (date.isBefore(now.toLocalDate())) return new ArrayList<>();
        if (dayEnd.isBefore(now)) return new ArrayList<>();

        List<Slot> slots = date.equals(now.toLocalDate())
                ? slotRepository.findAllSlotsForToday(findNextSlotStart(now), dayEnd)
                : slotRepository.findAllSlotsBetweenTimes(dayStart, dayEnd);

        return slots.stream()
                .filter(s -> isInsideWorkingWindow(s.getSlotTime(), s.getSlotTime().plusMinutes(SLOT_DURATION_MINUTES)))
                .toList();
    }

    @Transactional
    public List<Slot> reserveSlots(LocalDateTime startDateTime, int slotsNeeded) {
        LocalDateTime now = nowDateTime();
        if (!startDateTime.isAfter(now)) {
            throw new BookingException(BookingErrorCode.BOOKING_IN_PAST, "Không thể đặt lịch trong quá khứ.");
        }
        if (slotsNeeded <= 0) {
            throw new BookingException(BookingErrorCode.INVALID_TIME_RANGE, "Thời lượng đặt lịch không hợp lệ.");
        }

        LocalDateTime endDateTime = startDateTime.plusMinutes((long) slotsNeeded * SLOT_DURATION_MINUTES);
        if (!isInsideWorkingWindow(startDateTime, endDateTime)) {
            throw new BookingException(BookingErrorCode.OUTSIDE_WORKING_HOURS, "Thời gian nằm ngoài giờ làm việc.");
        }

        List<Slot> lockedSlots = slotRepository.findActiveSlotsForUpdate(startDateTime, endDateTime);
        if (lockedSlots.size() != slotsNeeded) return new ArrayList<>();

        for (int i = 0; i < lockedSlots.size(); i++) {
            Slot slot = lockedSlots.get(i);
            LocalDateTime expected = startDateTime.plusMinutes((long) i * SLOT_DURATION_MINUTES);
            if (!slot.getSlotTime().equals(expected) || !slot.isAvailable()) {
                return new ArrayList<>();
            }
        }

        for (Slot slot : lockedSlots) {
            slot.setBookedCount(slot.getBookedCount() + 1);
            slotRepository.save(slot);
        }

        logger.info("Reserved {} slots from {}", slotsNeeded, startDateTime);
        return lockedSlots;
    }

    @Transactional
    public void releaseSlots(List<Slot> slots) {
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
    public Appointment completeBooking(CustomerProfile customer,
                                       Services service,
                                       List<Slot> reservedSlots,
                                       String contactChannel,
                                       String contactValue,
                                       String notes) {
        if (reservedSlots.isEmpty()) {
            throw new BookingException(BookingErrorCode.SLOT_FULL, "Không có slot được giữ.");
        }

        Slot firstSlot = reservedSlots.get(0);
        Slot lastSlot = reservedSlots.get(reservedSlots.size() - 1);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setService(service);
        appointment.setDate(firstSlot.getSlotTime().toLocalDate());
        appointment.setStartTime(firstSlot.getStartTime());
        appointment.setEndTime(lastSlot.getEndTime());
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointment.setContactChannel(contactChannel);
        appointment.setContactValue(contactValue);
        appointment.setNotes(notes != null ? notes.trim() : null);

        for (int i = 0; i < reservedSlots.size(); i++) {
            appointment.addAppointmentSlot(new AppointmentSlot(appointment, reservedSlots.get(i), i));
        }
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment createPendingAppointment(CustomerProfile customer,
                                                Services service,
                                                List<Slot> reservedSlots,
                                                String contactChannel,
                                                String contactValue,
                                                String notes) {
        if (reservedSlots.isEmpty()) {
            throw new BookingException(BookingErrorCode.SLOT_FULL, "Không có slot được giữ.");
        }

        Slot firstSlot = reservedSlots.get(0);
        Slot lastSlot = reservedSlots.get(reservedSlots.size() - 1);

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setService(service);
        appointment.setDate(firstSlot.getSlotTime().toLocalDate());
        appointment.setStartTime(firstSlot.getStartTime());
        appointment.setEndTime(lastSlot.getEndTime());
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setContactChannel(contactChannel);
        appointment.setContactValue(contactValue);
        appointment.setNotes(notes != null ? notes.trim() : null);

        for (int i = 0; i < reservedSlots.size(); i++) {
            appointment.addAppointmentSlot(new AppointmentSlot(appointment, reservedSlots.get(i), i));
        }
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment confirmAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.PENDING_DEPOSIT) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể xác nhận lịch hẹn ở trạng thái hiện tại.");
        }
        appointment.setStatus(AppointmentStatus.CONFIRMED);
        return appointmentRepository.save(appointment);
    }

    @Transactional
    public Appointment cancelAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể hủy lịch với trạng thái hiện tại.");
        }

        List<Slot> slotsToRelease = appointment.getAppointmentSlots().stream()
                .map(AppointmentSlot::getSlot)
                .toList();

        appointment.setStatus(AppointmentStatus.CANCELLED);
        Appointment saved = appointmentRepository.save(appointment);
        releaseSlots(slotsToRelease);
        return saved;
    }

    @Transactional
    public Appointment assignDentist(Long appointmentId, Long dentistProfileId) {
        Appointment appointment = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.APPOINTMENT_NOT_FOUND, "Lịch hẹn không tồn tại."));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED || appointment.getStatus() == AppointmentStatus.COMPLETED) {
            throw new BookingException(BookingErrorCode.APPOINTMENT_STATUS_INVALID, "Không thể gán bác sĩ ở trạng thái hiện tại.");
        }

        LocalDate date = appointment.getDate();
        LocalTime startTime = appointment.getStartTime();
        LocalTime endTime = appointment.getEndTime();

        boolean hasOverlap = appointmentRepository.hasOverlappingAppointmentExcludingSelf(
                dentistProfileId, date, startTime, endTime, appointmentId);
        if (hasOverlap) {
            throw new BookingException(BookingErrorCode.BOOKING_CONFLICT, "Bác sĩ đã có lịch hẹn trong khung giờ này.");
        }

        DentistProfile dentist = new DentistProfile();
        dentist.setId(dentistProfileId);
        appointment.setDentist(dentist);
        return appointmentRepository.save(appointment);
    }

    public boolean checkDentistOverlap(Long dentistProfileId, LocalDate date,
                                       LocalTime startTime, LocalTime endTime, Long excludeAppointmentId) {
        if (excludeAppointmentId == null) {
            return appointmentRepository.hasOverlappingAppointment(dentistProfileId, date, startTime, endTime);
        }
        return appointmentRepository.hasOverlappingAppointmentExcludingSelf(
                dentistProfileId, date, startTime, endTime, excludeAppointmentId
        );
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
            if (isInsideWorkingWindow(current, current.plusMinutes(SLOT_DURATION_MINUTES))
                    && slotRepository.findBySlotTimeAndActiveTrue(current).isEmpty()) {
                slotRepository.save(new Slot(current, capacity));
                slotsCreated++;
            }
            current = current.plusMinutes(SLOT_DURATION_MINUTES);
        }
        logger.info("Initialized {} slots for date {}", slotsCreated, date);
    }

    @Transactional
    public void updateCapacityForDate(LocalDate date, int newCapacity) {
        List<Slot> slots = getAllSlotsForDate(date);
        for (Slot slot : slots) {
            if (slot.getBookedCount() > newCapacity) {
                throw new BookingException(BookingErrorCode.VALIDATION_ERROR,
                        "Không thể giảm sức chứa nhỏ hơn số lượng đã đặt.");
            }
            slot.setCapacity(newCapacity);
            slotRepository.save(slot);
        }
    }

    public Optional<Slot> getSlotById(Long slotId) {
        return slotRepository.findById(slotId);
    }

    @Transactional
    public List<Slot> reserveSlotById(Long slotId, int slotsNeeded) {
        Slot selectedSlot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giờ không tồn tại."));
        return reserveSlots(selectedSlot.getSlotTime(), slotsNeeded);
    }

    @Transactional
    public void releaseSlotById(Long slotId) {
        Slot slot = slotRepository.findById(slotId)
                .orElseThrow(() -> new BookingException(BookingErrorCode.SLOT_NOT_FOUND, "Khung giờ không tồn tại."));
        releaseSlots(List.of(slot));
    }

    private LocalDateTime findNextSlotStart(LocalDateTime now) {
        int slotMinute = (now.getMinute() / SLOT_DURATION_MINUTES) * SLOT_DURATION_MINUTES;
        LocalDateTime currentSlotStart = LocalDateTime.of(now.toLocalDate(), LocalTime.of(now.getHour(), slotMinute));
        return now.isAfter(currentSlotStart) ? currentSlotStart.plusMinutes(SLOT_DURATION_MINUTES) : currentSlotStart;
    }

    private boolean isInsideWorkingWindow(LocalDateTime startDateTime, LocalDateTime endDateTime) {
        if (endDateTime.toLocalDate().isAfter(startDateTime.toLocalDate())) return false;

        LocalTime start = startDateTime.toLocalTime();
        LocalTime end = endDateTime.toLocalTime();

        if (start.isBefore(CLINIC_OPEN_TIME) || !end.isAfter(start) || end.isAfter(CLINIC_CLOSE_TIME) || start.equals(CLINIC_CLOSE_TIME)) {
            return false;
        }
        return !(start.isBefore(LUNCH_END_TIME) && end.isAfter(LUNCH_START_TIME));
    }

    private LocalDateTime nowDateTime() {
        return LocalDateTime.now(CLINIC_ZONE);
    }
}
