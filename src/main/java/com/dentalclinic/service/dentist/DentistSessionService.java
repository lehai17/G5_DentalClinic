package com.dentalclinic.service.dentist;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.model.medical.MedicalFinding;
import com.dentalclinic.model.medical.MedicalImage;
import com.dentalclinic.model.medical.MedicalProposedService;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.model.payment.BillingPerformedService;
import com.dentalclinic.model.payment.BillingPrescriptionItem;
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
       EXAMINATION
       ========================================================= */

    public record ExamForm(
            String patientName,
            com.dentalclinic.model.medical.MedicalRecord record
    ) {}

    @Transactional(readOnly = true)
    public ExamForm loadExam(Long appointmentId, Long customerUserId) {
        Appointment appt = mustGetAppointment(appointmentId, customerUserId);

        MedicalRecord mr = medicalRecordRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElse(null);

        if (mr == null) {
            mr = new MedicalRecord();
            mr.setAppointment(appt);
        }

        return new ExamForm(appt.getCustomer().getFullName(), mr);
    }

    @Transactional
    public void saveExam(Long appointmentId,
                         Long customerUserId,
                         MedicalRecord form) {

        Appointment appt = mustGetAppointment(appointmentId, customerUserId);

        if (appt.getStatus() == AppointmentStatus.DONE
                || appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Appointment already finalized");
        }

        MedicalRecord mr = medicalRecordRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElseGet(() -> {
                    MedicalRecord x = new MedicalRecord();
                    x.setAppointment(appt);
                    return x;
                });

        mr.setDiagnosis(form.getDiagnosis());
        mr.setSecondaryDiagnosis(form.getSecondaryDiagnosis());
        mr.setComplaintCode(form.getComplaintCode());
        mr.setComplaintNote(form.getComplaintNote());
        mr.setClinicalNotes(form.getClinicalNotes());

        // replace child collections
        mr.getFindings().clear();
        if (form.getFindings() != null) {
            for (MedicalFinding f : form.getFindings()) {
                f.setMedicalRecord(mr);
                mr.getFindings().add(f);
            }
        }
        mr.getImages().clear();
        if (form.getImages() != null) {
            for (MedicalImage i : form.getImages()) {
                i.setMedicalRecord(mr);
                mr.getImages().add(i);
            }
        }
        mr.getProposedServices().clear();
        if (form.getProposedServices() != null) {
            for (MedicalProposedService ps : form.getProposedServices()) {
                ps.setMedicalRecord(mr);
                mr.getProposedServices().add(ps);
            }
        }

        medicalRecordRepository.save(mr);
    }

    /* =========================================================
       BILLING
       ========================================================= */

    public record BillingForm(
            String patientName,
            BillingNote billingNote
    ) {}

    @Transactional(readOnly = true)
    public BillingForm loadBilling(Long appointmentId, Long customerUserId) {
        Appointment appt = mustGetAppointment(appointmentId, customerUserId);

        BillingNote bn = billingNoteRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElse(null);

        if (bn == null) {
            bn = new BillingNote();
            bn.setAppointment(appt);
            // default performed service based on appointment
            if (appt.getService() != null) {
                BillingPerformedService ps = new BillingPerformedService();
                ps.setBillingNote(bn);
                ps.setService(appt.getService());
                ps.setQty(1);
                ps.setToothNo("");
                bn.getPerformedServices().add(ps);
            }
        }

        return new BillingForm(appt.getCustomer().getFullName(), bn);
    }

    @Transactional
    public void saveBilling(Long appointmentId,
                            Long customerUserId,
                            BillingNote form) {

        Appointment appt = mustGetAppointment(appointmentId, customerUserId);

        if (appt.getStatus() == AppointmentStatus.DONE
                || appt.getStatus() == AppointmentStatus.COMPLETED) {
            throw new IllegalStateException("Appointment already finalized");
        }

        BillingNote bn = billingNoteRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElseGet(() -> {
                    BillingNote x = new BillingNote();
                    x.setAppointment(appt);
                    return x;
                });

        // copy fields from form
        bn.setNote(form.getNote());

        bn.getPerformedServices().clear();
        if (form.getPerformedServices() != null) {
            for (BillingPerformedService ps : form.getPerformedServices()) {
                ps.setBillingNote(bn);
                bn.getPerformedServices().add(ps);
            }
        }

        bn.getPrescriptionItems().clear();
        if (form.getPrescriptionItems() != null) {
            for (BillingPrescriptionItem pi : form.getPrescriptionItems()) {
                pi.setBillingNote(bn);
                bn.getPrescriptionItems().add(pi);
            }
        }

        billingNoteRepository.save(bn);

        appt.setStatus(AppointmentStatus.DONE);
        appointmentRepository.save(appt);
    }

    /* =========================================================
       INTERNAL
       ========================================================= */

    private Appointment mustGetAppointment(Long appointmentId, Long customerUserId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Appointment not found"));

        Long ownerUserId = appt.getCustomer().getUser().getId();
        if (!ownerUserId.equals(customerUserId)) {
            throw new IllegalArgumentException("Appointment does not belong to this customer");
        }
        return appt;
    }

    // no more JSON helpers; all handling is via relational entities
}