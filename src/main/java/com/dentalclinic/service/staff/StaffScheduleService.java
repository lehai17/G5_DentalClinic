package com.dentalclinic.service.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class StaffScheduleService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    /* =========================
       VIEW DENTIST SCHEDULE BY DATE
       ========================= */
    public List<Appointment> getDentistSchedule(Long dentistId, LocalDate date) {
        return appointmentRepository.findAll().stream()
                .filter(a ->
                        a.getDentist() != null &&
                                a.getDentist().getId().equals(dentistId) &&
                                a.getDate().equals(date)
                )
                .toList();
    }
}
