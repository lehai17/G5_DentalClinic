package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.DentistScheduleRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.model.service.Services;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class CustomerAppointmentService {

    private final CustomerProfileRepository customerProfileRepository;
    private final AppointmentRepository appointmentRepository;
    private final DentistScheduleRepository dentistScheduleRepository;
    private final ServiceRepository serviceRepository;

    public CustomerAppointmentService(CustomerProfileRepository customerProfileRepository,
                                     AppointmentRepository appointmentRepository,
                                     DentistScheduleRepository dentistScheduleRepository,
                                     ServiceRepository serviceRepository) {
        this.customerProfileRepository = customerProfileRepository;
        this.appointmentRepository = appointmentRepository;
        this.dentistScheduleRepository = dentistScheduleRepository;
        this.serviceRepository = serviceRepository;
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
                    boolean busy = appointmentRepository.existsByDentist_IdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                            slot.getDentist().getId(),
                            slot.getDate(),
                            slot.getEndTime(),
                            slot.getStartTime()
                    );
                    dto.setAvailable(!busy);
                    return dto;
                })
                .filter(SlotDto::isAvailable)
                .collect(Collectors.toList());
    }

    @Transactional
    public AppointmentDto createAppointment(Long userId, CreateAppointmentRequest request) {
        CustomerProfile customer = customerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found for user"));
        DentistSchedule slot = dentistScheduleRepository.findById(request.getSlotId())
                .orElseThrow(() -> new IllegalArgumentException("Slot not found"));
        Services service = serviceRepository.findById(request.getServiceId())
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setSlot(slot);
        appointment.setDentist(slot.getDentist());
        appointment.setService(service);
        appointment.setDate(slot.getDate());
        appointment.setStartTime(slot.getStartTime());
        appointment.setEndTime(slot.getEndTime());
        appointment.setStatus(AppointmentStatus.PENDING_DEPOSIT);
        appointment.setNotes(request.getPatientNote());
        appointment.setContactChannel(request.getContactChannel());
        appointment.setContactValue(request.getContactValue());
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
