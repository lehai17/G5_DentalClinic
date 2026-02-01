package com.dentalclinic.service.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
import com.dentalclinic.repository.MedicalRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DentistSessionService {

    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final BillingNoteRepository billingNoteRepository;

    public DentistSessionService(AppointmentRepository appointmentRepository,
                                 MedicalRecordRepository medicalRecordRepository,
                                 BillingNoteRepository billingNoteRepository) {
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.billingNoteRepository = billingNoteRepository;
    }

    /* =========================================================
       LOGIN / SESSION
       ========================================================= */

    /**
     * Tạm thời chưa nối login → controller bắt buộc truyền dentistUserId
     * Khi có Spring Security → sửa method này là đủ
     */
    public Long getCurrentDentistUserId() {
        return 1L;
    }

    /* =========================================================
       EXAMINATION
       ========================================================= */

    public record ExamForm(
            String patientName,
            String diagnosis,
            String treatmentNote
    ) {}

    @Transactional(readOnly = true)
    public ExamForm loadExam(Long appointmentId, Long customerUserId) {

        Appointment appt = mustGetAppointment(appointmentId, customerUserId);

        MedicalRecord mr = medicalRecordRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElse(null);

        String patientName = appt.getCustomer().getFullName();

        return new ExamForm(
                patientName,
                mr == null ? "" : safe(mr.getDiagnosis()),
                mr == null ? "" : safe(mr.getTreatmentNote())
        );
    }

    @Transactional
    public void saveExam(Long appointmentId,
                         Long customerUserId,
                         String diagnosis,
                         String treatmentNote) {

        Appointment appt = mustGetAppointment(appointmentId, customerUserId);

        MedicalRecord mr = medicalRecordRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElseGet(() -> {
                    MedicalRecord x = new MedicalRecord();
                    x.setAppointment(appt);
                    return x;
                });

        mr.setDiagnosis(safe(diagnosis));
        mr.setTreatmentNote(safe(treatmentNote));
        medicalRecordRepository.save(mr);

        appt.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appt);
    }

    /* =========================================================
       BILLING
       ========================================================= */

    public record BillingForm(
            String patientName,
            String performedServicesJson,
            String prescriptionNote,
            String note
    ) {}

    @Transactional(readOnly = true)
    public BillingForm loadBilling(Long appointmentId, Long customerUserId) {

        Appointment appt = mustGetAppointment(appointmentId, customerUserId);

        BillingNote bn = billingNoteRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElse(null);

        String patientName = appt.getCustomer().getFullName();

        return new BillingForm(
                patientName,
                bn == null ? defaultPerformedJson() : safe(bn.getPerformedServicesJson()),
                bn == null ? defaultPrescriptionJson() : safe(bn.getPrescriptionNote()),
                bn == null ? "" : safe(bn.getNote())
        );
    }

    @Transactional
    public void saveBilling(Long appointmentId,
                            Long customerUserId,
                            String performedServicesJson,
                            String prescriptionNote,
                            String note) {

        Appointment appt = mustGetAppointment(appointmentId, customerUserId);

        BillingNote bn = billingNoteRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElseGet(() -> {
                    BillingNote x = new BillingNote();
                    x.setAppointment(appt);
                    return x;
                });

        bn.setPerformedServicesJson(safe(performedServicesJson));
        bn.setPrescriptionNote(safe(prescriptionNote));
        bn.setNote(safe(note));
        billingNoteRepository.save(bn);

        appt.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(appt);
    }

    /* =========================================================
       INTERNAL HELPERS
       ========================================================= */

    private Appointment mustGetAppointment(Long appointmentId, Long customerUserId) {

        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Appointment not found")
                );

        if (appt.getCustomer() == null ||
                appt.getCustomer().getUser() == null) {
            throw new IllegalArgumentException("Appointment missing customer");
        }

        Long ownerUserId = appt.getCustomer().getUser().getId();
        if (!ownerUserId.equals(customerUserId)) {
            throw new IllegalArgumentException("Appointment does not belong to this customer");
        }

        return appt;
    }

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private String defaultPerformedJson() {
        return """
            [
              {"serviceId":1,"qty":1,"toothNo":"Full mouth"},
              {"serviceId":2,"qty":1,"toothNo":"36"}
            ]
        """;
    }

    private String defaultPrescriptionJson() {
        return """
            [
              {"name":"Amoxicillin","dosage":"500mg","duration":"5 days","instruction":"After meals"}
            ]
        """;
    }
}
