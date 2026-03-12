package com.dentalclinic.service.medical;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.MedicalRecordRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final AppointmentRepository appointmentRepository;

    public MedicalRecordService(
            MedicalRecordRepository medicalRecordRepository,
            AppointmentRepository appointmentRepository
    ) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.appointmentRepository = appointmentRepository;
    }

    // âœ… DÙNG CHO GET
    public Optional<MedicalRecord> findByAppointmentId(Long appointmentId) {
        return medicalRecordRepository.findByAppointment_Id(appointmentId);
    }

    // âœ… UPSERT â€“ KHÔNG T� O RECORD MỚI
    @Transactional
    public MedicalRecord saveOrUpdate(
            Long appointmentId,
            MedicalRecord form
    ) {
        MedicalRecord record =
                medicalRecordRepository
                        .findByAppointment_Id(appointmentId)
                        .orElse(null);

        if (record == null) {
            Appointment appointment =
                    appointmentRepository.findById(appointmentId)
                            .orElseThrow();

            record = new MedicalRecord();
            record.setAppointment(appointment);
        }

        record.setDiagnosis(form.getDiagnosis());

        // copy child lists
        record.getFindings().clear();
        if (form.getFindings() != null) {
            for (com.dentalclinic.model.medical.MedicalFinding f : form.getFindings()) {
                f.setMedicalRecord(record);
                record.getFindings().add(f);
            }
        }
        record.getImages().clear();
        if (form.getImages() != null) {
            for (com.dentalclinic.model.medical.MedicalImage i : form.getImages()) {
                i.setMedicalRecord(record);
                record.getImages().add(i);
            }
        }
        record.getProposedServices().clear();
        if (form.getProposedServices() != null) {
            for (com.dentalclinic.model.medical.MedicalProposedService ps : form.getProposedServices()) {
                ps.setMedicalRecord(record);
                record.getProposedServices().add(ps);
            }
        }

        return medicalRecordRepository.save(record);
    }
}

