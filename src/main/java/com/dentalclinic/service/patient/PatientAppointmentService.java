package com.dentalclinic.service.patient;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.appointment.TimeSlot;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.ServiceRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PatientAppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final DentistProfileRepository dentistProfileRepository;
    private final ServiceRepository serviceRepository;

    public PatientAppointmentService(
            AppointmentRepository appointmentRepository,
            CustomerProfileRepository customerProfileRepository,
            DentistProfileRepository dentistProfileRepository,
            ServiceRepository serviceRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.dentistProfileRepository = dentistProfileRepository;
        this.serviceRepository = serviceRepository;
    }

    // =========================
    // Get active services (for booking)
    // =========================
    public List<Services> getActiveServices() {
        return serviceRepository.findByActiveTrue();
    }

    // =========================
    // Create Appointment
    // =========================
    public Appointment createAppointment(
            Long customerUserId,
            Long dentistUserId,
            Long serviceId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime
    ) {

        boolean busy = appointmentRepository
                .existsByDentist_IdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                        dentistUserId, date, startTime, endTime
                );

        if (busy) {
            throw new IllegalStateException("Dentist is not available at this time slot");
        }

        CustomerProfile customer = customerProfileRepository
                .findByUser_Id(customerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));

        DentistProfile dentist = dentistProfileRepository
                .findById(dentistUserId)
                .orElseThrow(() -> new IllegalArgumentException("Dentist not found"));

        Services service = serviceRepository
                .findById(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setDentist(dentist);
        appointment.setService(service);
        appointment.setDate(date);
        appointment.setStartTime(startTime);
        appointment.setEndTime(endTime);
        appointment.setStatus(AppointmentStatus.PENDING);

        return appointmentRepository.save(appointment);
    }

    // =========================
    // View appointment detail
    // =========================
    public Appointment getAppointmentDetail(
            Long appointmentId,
            Long customerUserId
    ) {
        return appointmentRepository
                .findByIdAndCustomer_User_Id(appointmentId, customerUserId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));
    }

    // =========================
    // View request status
    // =========================
    public List<Appointment> getAppointmentsByStatus(
            Long customerUserId,
            AppointmentStatus status
    ) {
        return appointmentRepository
                .findByCustomer_User_IdAndStatus(customerUserId, status);
    }

    // =========================
    // Appointment history
    // =========================
    public List<Appointment> getAppointmentHistory(Long customerUserId) {
        return appointmentRepository
                .findByCustomer_User_IdOrderByDateDesc(customerUserId);
    }

    // =========================
    // Get all dentists
    // =========================
    public List<DentistProfile> getAllDentists() {
        return dentistProfileRepository.findAll();
    }

    // =========================
    // Get available time slots for a dentist on a date
    // =========================
    public List<TimeSlot> getAvailableTimeSlots(Long dentistUserId, LocalDate date) {
        // Generate standard time slots (9:00 AM to 5:00 PM, 1 hour slots)
        List<TimeSlot> allSlots = new ArrayList<>();
        LocalTime start = LocalTime.of(9, 0);
        LocalTime end = LocalTime.of(17, 0);
        
        while (start.isBefore(end)) {
            LocalTime slotEnd = start.plusHours(1);
            allSlots.add(new TimeSlot(start, slotEnd, true));
            start = slotEnd;
        }

        // Get existing appointments for this dentist on this date
        // We need to check all appointments and filter by dentist and date
        List<Appointment> allAppointments = appointmentRepository.findAll();
        List<Appointment> existingAppointments = allAppointments.stream()
                .filter(apt -> apt.getDentist() != null 
                        && apt.getDentist().getUser() != null
                        && apt.getDentist().getUser().getId().equals(dentistUserId)
                        && apt.getDate().equals(date)
                        && (apt.getStatus() == AppointmentStatus.PENDING 
                            || apt.getStatus() == AppointmentStatus.CONFIRMED))
                .collect(Collectors.toList());

        // Mark slots as unavailable if they overlap with existing appointments
        for (Appointment apt : existingAppointments) {
            for (TimeSlot slot : allSlots) {
                if (isTimeOverlap(slot.getStartTime(), slot.getEndTime(), 
                                 apt.getStartTime(), apt.getEndTime())) {
                    slot.setAvailable(false);
                }
            }
        }

        return allSlots;
    }

    private boolean isTimeOverlap(LocalTime start1, LocalTime end1, 
                                 LocalTime start2, LocalTime end2) {
        return start1.isBefore(end2) && start2.isBefore(end1);
    }
}
