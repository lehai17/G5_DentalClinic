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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DentistSessionService {

    private static final Logger logger = LoggerFactory.getLogger(DentistSessionService.class);

    private final AppointmentRepository appointmentRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final BillingNoteRepository billingNoteRepository;
    private final ReexamService reexamService;

    public DentistSessionService(AppointmentRepository appointmentRepository,
                                 MedicalRecordRepository medicalRecordRepository,
                                 BillingNoteRepository billingNoteRepository,
                                 ReexamService reexamService) {
        this.appointmentRepository = appointmentRepository;
        this.medicalRecordRepository = medicalRecordRepository;
        this.billingNoteRepository = billingNoteRepository;
        this.reexamService = reexamService;
    }

    /* =========================================================
       EXAMINATION
       ========================================================= */

    public record ExamForm(
            String patientName,
            com.dentalclinic.model.medical.MedicalRecord record
    ) {}

    @Transactional(readOnly = true)
    public Appointment loadOwnedAppointmentWithDetails(Long appointmentId,
                                                       Long customerUserId,
                                                       Long dentistUserId) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay lich hen"));
        initializeAppointmentDetails(appt);
        validateAppointmentOwnership(appt, customerUserId, dentistUserId);
        return appt;
    }

    @Transactional(readOnly = true)
    public ExamForm loadExam(Long appointmentId, Long customerUserId, Long dentistUserId) {
        Appointment appt = mustGetAppointment(appointmentId, customerUserId, dentistUserId);

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
                         Long dentistUserId,
                         MedicalRecord form) {

        Appointment appt = mustGetAppointment(appointmentId, customerUserId, dentistUserId);

        validateAppointmentNotFinalized(appt);

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
        mr.setComplaintNote(form.getComplaintNote());
        mr.setClinicalNotes(form.getClinicalNotes());

        // replace child collections
        mr.getFindings().clear();
        if (form.getFindings() != null) {
            for (MedicalFinding f : form.getFindings()) {

                boolean isEmpty =
                        (f.getToothNo() == null || f.getToothNo().isBlank()) &&
                                (f.getCondition() == null || f.getCondition().isBlank()) &&
                                (f.getSeverity() == null || f.getSeverity().isBlank()) &&
                                (f.getNote() == null || f.getNote().isBlank());

                if (isEmpty) continue; // 🔥 bỏ dòng rỗng

                f.setMedicalRecord(mr);
                mr.getFindings().add(f);
            }
        }
        mr.getImages().clear();
        if (form.getImages() != null) {
            for (MedicalImage i : form.getImages()) {

                // URL is required for persistence (medical_image.url is NOT NULL).
                if (i.getUrl() == null || i.getUrl().isBlank()) {
                    continue;
                }

                boolean isEmpty =
                        (i.getUrl() == null || i.getUrl().isBlank()) &&
                                (i.getType() == null || i.getType().isBlank()) &&
                                (i.getNote() == null || i.getNote().isBlank());

                if (isEmpty) continue; // 🔥 bỏ dòng rỗng

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
    public BillingForm loadBilling(Long appointmentId, Long customerUserId, Long dentistUserId) {
        Appointment appt = mustGetAppointment(appointmentId, customerUserId, dentistUserId);
        if (!isBillingViewAllowed(appt.getStatus())) {
            throw new IllegalStateException("Billing view is not allowed for this status");
        }

        BillingNote bn = billingNoteRepository
                .findByAppointment_IdAndAppointment_Customer_User_Id(
                        appointmentId, customerUserId
                )
                .orElse(null);

        if (bn == null) {
            bn = new BillingNote();
            bn.setAppointment(appt);
            // default performed services based on appointment details
            if (appt.getAppointmentDetails() != null && !appt.getAppointmentDetails().isEmpty()) {
                for (var detail : appt.getAppointmentDetails()) {
                    if (detail.getService() == null) {
                        continue;
                    }
                    BillingPerformedService ps = new BillingPerformedService();
                    ps.setBillingNote(bn);
                    ps.setService(detail.getService());
                    ps.setQty(1);
                    ps.setToothNo("");
                    bn.getPerformedServices().add(ps);
                }
            } else if (appt.getService() != null) {
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
                            Long dentistUserId,
                            BillingNote form) {

        Appointment appt = mustGetAppointment(appointmentId, customerUserId, dentistUserId);
        if (appt.getStatus() != AppointmentStatus.EXAMINING) {
            throw new IllegalStateException("Only allowed when appointment is EXAMINING");
        }

        validateAppointmentNotFinalized(appt);

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

                if (ps.getQty() <= 0) ps.setQty(1);

                ps.setBillingNote(bn);
                bn.getPerformedServices().add(ps);
            }
        }

        bn.getPrescriptionItems().clear();
        if (form.getPrescriptionItems() != null) {
            for (BillingPrescriptionItem pi : form.getPrescriptionItems()) {

                boolean isEmpty =
                        (pi.getMedicineName() == null || pi.getMedicineName().isBlank()) &&
                                (pi.getDosage() == null || pi.getDosage().isBlank()) &&
                                (pi.getNote() == null || pi.getNote().isBlank());

                if (isEmpty) continue; // 🔥 bỏ dòng trống

                pi.setBillingNote(bn);
                bn.getPrescriptionItems().add(pi);
            }
        }

        billingNoteRepository.save(bn);
        appt.setStatus(AppointmentStatus.DONE);
        appointmentRepository.save(appt);
        appointmentRepository.flush();  // Force flush to DB
        
        // Auto-confirm any pending reexams for this appointment
        logger.info("[SESSION] Appointment {} status changed to DONE, attempting to auto-confirm reexam", appt.getId());
        try {
            // Debug: check database directly
            var debugData = appointmentRepository.debugFindReexamByOriginalId(appt.getId());
            logger.info("[SESSION] Debug: Found {} reexam records for appointment ID: {}", debugData.size(), appt.getId());
            for (Object[] row : debugData) {
                logger.info("[SESSION] Debug: id={}, original_appointment_id={}, status={}", row[0], row[1], row[2]);
            }
            
            // Find reexam for this appointment
            var reexamOpt = appointmentRepository.findReexamByOriginalAppointmentId(appt.getId());
            if (reexamOpt.isPresent()) {
                Appointment reexam = reexamOpt.get();
                logger.info("[SESSION] Found reexam ID: {} with status: {}", reexam.getId(), reexam.getStatus());
                
                if (reexam.getStatus() == AppointmentStatus.REEXAM) {
                    logger.info("[SESSION] Updating reexam ID: {} from REEXAM to CONFIRMED", reexam.getId());
                    reexam.setStatus(AppointmentStatus.CONFIRMED);
                    appointmentRepository.save(reexam);
                    appointmentRepository.flush();
                    logger.info("[SESSION] Reexam ID: {} successfully updated to CONFIRMED", reexam.getId());
                } else {
                    logger.info("[SESSION] Reexam ID: {} has status: {}, not updating", reexam.getId(), reexam.getStatus());
                }
            } else {
                logger.info("[SESSION] No reexam found for appointment ID: {}", appt.getId());
            }
        } catch (Exception e) {
            logger.error("[SESSION] Error auto-confirming reexam", e);
            e.printStackTrace();
        }
    }

    /* =========================================================
       INTERNAL
       ========================================================= */

    private Appointment mustGetAppointment(Long appointmentId, Long customerUserId, Long dentistUserId) {
        Appointment appt = appointmentRepository.findByIdWithDetails(appointmentId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy lịch hẹn"));

        initializeAppointmentDetails(appt);
        validateAppointmentOwnership(appt, customerUserId, dentistUserId);
        return appt;
    }

    private void initializeAppointmentDetails(Appointment appointment) {
        if (appointment == null || appointment.getAppointmentDetails() == null) {
            return;
        }

        appointment.getAppointmentDetails().forEach(detail -> {
            if (detail.getService() != null) {
                detail.getService().getId();
            }
        });
    }

    private void validateAppointmentOwnership(Appointment appt, Long customerUserId, Long dentistUserId) {
        Long ownerUserId = appt.getCustomer().getUser().getId();
        if (!ownerUserId.equals(customerUserId)) {
            throw new IllegalArgumentException("Appointment does not belong to this customer");
        }

        if (appt.getDentist() == null
                || appt.getDentist().getUser() == null
                || !appt.getDentist().getUser().getId().equals(dentistUserId)) {
            throw new IllegalArgumentException("Ban khong co quyen truy cap lich hen nay");
        }
    }

    /**
     * Validate that appointment is not in finalized state (DONE, COMPLETED, WAITING_PAYMENT)
     */
    private void validateAppointmentNotFinalized(Appointment appt) {
        if (appt.getStatus() == AppointmentStatus.DONE
                || appt.getStatus() == AppointmentStatus.COMPLETED
                || appt.getStatus() == AppointmentStatus.WAITING_PAYMENT) {
            throw new IllegalStateException("Appointment already finalized");
        }
    }

    private boolean isBillingViewAllowed(AppointmentStatus status) {
        if (status == null) {
            return false;
        }
        return status == AppointmentStatus.EXAMINING
                || status == AppointmentStatus.DONE
                || status == AppointmentStatus.WAITING_PAYMENT
                || status == AppointmentStatus.COMPLETED;
    }

    // no more JSON helpers; all handling is via relational entities
}
