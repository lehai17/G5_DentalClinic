package com.dentalclinic.service.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.service.mail.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StaffAppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DentistProfileRepository dentistProfileRepository;

    @Autowired
    private EmailService emailService;


    /* VIEW ALL APPOINTMENTS (STAFF) */
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    /* CONFIRM APPOINTMENT */
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

    /* ASSIGN / REASSIGN DENTIST */
    public void assignDentist(Long appointmentId, Long dentistId) {

        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        int busy = appointmentRepository.countBusyAppointmentsExcludeSelf(
                dentistId,
                appt.getDate(),
                appt.getStartTime(),
                appt.getEndTime(),
                appt.getId()
        );

        if (busy > 0) {
            throw new RuntimeException("Bác sĩ đã có lịch trong khung giờ này");
        }

        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Dentist not found"));

        appt.setDentist(dentist);
        appointmentRepository.save(appt);
    }


    public void completeAppointment(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (a.getStatus() != AppointmentStatus.CHECKED_IN) {
            throw new RuntimeException("Only CHECKED_IN appointment can be completed");
        }

        a.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(a);
    }


    /* CANCEL APPOINTMENT */
    public void cancelAppointment(Long appointmentId, String reason) {

        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setNotes(reason);
        appointmentRepository.save(appt);
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
    public void checkInAppointment(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuntimeException("Only CONFIRMED appointment can be checked-in");
        }

        a.setStatus(AppointmentStatus.CHECKED_IN);
        appointmentRepository.save(a);
    }

}
