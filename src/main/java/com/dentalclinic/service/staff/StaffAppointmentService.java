package com.dentalclinic.service.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import com.dentalclinic.service.mail.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffAppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DentistProfileRepository dentistProfileRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private CustomerAppointmentService customerAppointmentService;

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Transactional
    public void confirmAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getDentist() == null) {
            throw new RuntimeException("Phải gán bác sĩ trước khi xác nhận");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Only PENDING appointment can be confirmed");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appointment);

        emailService.sendAppointmentConfirmed(appointment);
    }

    @Transactional
    public void assignDentist(Long appointmentId, Long dentistId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        boolean hasOverlap = customerAppointmentService.checkDentistOverlap(
                dentistId,
                appt.getDate(),
                appt.getStartTime(),
                appt.getEndTime(),
                appt.getId()
        );

        if (hasOverlap) {
            throw new RuntimeException("Bác sĩ đã có lịch trong khung giờ này");
        }

        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Dentist not found"));

        appt.setDentist(dentist);
        appointmentRepository.save(appt);
    }

    @Transactional
    public void completeAppointment(Long id) {
        Appointment a = appointmentRepository.findById(id).orElseThrow();
        if (a.getStatus() == AppointmentStatus.CONFIRMED) {
            a.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(a);
        }
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, String reason) {
        Appointment appt = appointmentRepository.findByIdWithSlots(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appt.getStatus() == AppointmentStatus.CANCELLED || 
            appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new RuntimeException("Cannot cancel appointment in status: " + appt.getStatus());
        }

        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setNotes(reason);
        appointmentRepository.save(appt);

        // reuse internal cancel logic from customer service
        customerAppointmentService.cancelAppointmentInternal(appointmentId);
    }

    public Page<Appointment> searchAndSort(
            String keyword,
            String sort,
            int page
    ) {
        Sort s = Sort.by("date").ascending();

        if ("newest".equals(sort)) {
            s = Sort.by("date").descending();
        } else if ("oldest".equals(sort)) {
            s = Sort.by("date").ascending();
        }

        Pageable pageable = PageRequest.of(page, 3, s);

        if (keyword != null && !keyword.trim().isEmpty()) {
            return appointmentRepository
                    .findByCustomer_FullNameContainingIgnoreCase(keyword, pageable);
        }

        return appointmentRepository.findAll(pageable);
    }
}
