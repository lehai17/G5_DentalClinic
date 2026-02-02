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

    // ✅ DÙNG CHO GET
    public Optional<MedicalRecord> findByAppointmentId(Long appointmentId) {
        return medicalRecordRepository.findByAppointment_Id(appointmentId);
    }

    // ✅ UPSERT – KHÔNG TẠO RECORD MỚI
    @Transactional
    public MedicalRecord saveOrUpdate(
            Long appointmentId,
            String diagnosis,
            String treatmentNote
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

        record.setDiagnosis(diagnosis);
        record.setTreatmentNote(treatmentNote);

        return medicalRecordRepository.save(record);
    }
}
