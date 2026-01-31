package com.dentalclinic.service.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.model.profile.DentistProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StaffAppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DentistProfileRepository dentistProfileRepository;

    /* =========================
       VIEW ALL APPOINTMENTS (STAFF)
       ========================= */
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    /* =========================
       CONFIRM APPOINTMENT
       ========================= */
    public void confirmAppointment(Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appt.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Only PENDING appointment can be confirmed");
        }

        appt.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appt);
    }

    /* =========================
       ASSIGN / REASSIGN DENTIST
       ========================= */
    public void assignDentist(Long appointmentId, Long dentistId) {

        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        boolean dentistBusy =
                appointmentRepository
                        .existsByDentist_IdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
                                dentistId,
                                appt.getDate(),
                                appt.getEndTime(),
                                appt.getStartTime()
                        );

        if (dentistBusy) {
            throw new RuntimeException("Dentist is busy at this time");
        }

        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Dentist not found"));

        appt.setDentist(dentist);
        appointmentRepository.save(appt);
    }

    /* =========================
       CHECK-IN
       ========================= */
    public void checkIn(Long appointmentId) {

        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appt.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuntimeException("Only CONFIRMED appointment can be checked in");
        }

        appt.setStatus(AppointmentStatus.CHECKED_IN);
        appointmentRepository.save(appt);
    }

    /* =========================
       CANCEL APPOINTMENT
       ========================= */
    public void cancelAppointment(Long appointmentId, String reason) {

        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        appt.setStatus(AppointmentStatus.CANCELLED);
        appt.setNotes(reason);
        appointmentRepository.save(appt);
    }
}
