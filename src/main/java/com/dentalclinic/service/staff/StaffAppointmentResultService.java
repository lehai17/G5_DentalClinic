package com.dentalclinic.service.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.MedicalRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAppointmentResultService {

    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;

    public StaffAppointmentResultService(AppointmentRepository appointmentRepository,
                                         MedicalRecordRepository medicalRecordRepository) {
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
    }

    public record AppointmentResult(Appointment appointment, MedicalRecord medicalRecord) {}

    @Transactional(readOnly = true)
    public AppointmentResult load(Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        MedicalRecord mr = medicalRecordRepository.findByAppointment_Id(appointmentId).orElse(null);

        if (mr != null) {
            if (mr.getFindings() != null) {
                mr.getFindings().size();
            }

            if (mr.getImages() != null) {
                mr.getImages().size();
            }

            if (mr.getProposedServices() != null) {
                mr.getProposedServices().size();
                mr.getProposedServices().forEach(ps -> {
                    if (ps.getService() != null) {
                        ps.getService().getName();
                    }
                });
            }
        }

        return new AppointmentResult(appt, mr);
    }
}