package com.dentalclinic.service.medical;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.medical.MedicalFinding;
import com.dentalclinic.model.medical.MedicalImage;
import com.dentalclinic.model.medical.MedicalRecord;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.model.payment.BillingPerformedService;
import com.dentalclinic.model.payment.BillingPrescriptionItem;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
import com.dentalclinic.repository.MedicalRecordRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class MedicalRecordService {

    private final MedicalRecordRepository medicalRecordRepository;
    private final AppointmentRepository appointmentRepository;
    private final BillingNoteRepository billingNoteRepository;

    public MedicalRecordService(
            MedicalRecordRepository medicalRecordRepository,
            AppointmentRepository appointmentRepository,
            BillingNoteRepository billingNoteRepository
    ) {
        this.medicalRecordRepository = medicalRecordRepository;
        this.appointmentRepository = appointmentRepository;
        this.billingNoteRepository = billingNoteRepository;
    }

    // âœ… DÙNG CHO GET
    public Optional<MedicalRecord> findByAppointmentId(Long appointmentId) {
        return medicalRecordRepository.findByAppointment_Id(appointmentId);
    }

    @Transactional
    public List<ReexamHistoryStepView> findReexamHistorySteps(Long appointmentId) {
        Appointment current = appointmentRepository.findById(appointmentId)
                .orElseThrow();

        if (current.getOriginalAppointment() == null) {
            return Collections.emptyList();
        }

        Map<Long, Appointment> priorAppointments = new HashMap<>();
        List<Long> orderedIds = new ArrayList<>();
        Set<Long> visited = new HashSet<>();

        Appointment cursor = current.getOriginalAppointment();
        while (cursor != null && cursor.getId() != null && visited.add(cursor.getId())) {
            cursor.getDate();
            cursor.getStartTime();
            cursor.getEndTime();
            cursor.getStatus();
            priorAppointments.put(cursor.getId(), cursor);
            orderedIds.add(cursor.getId());
            cursor = cursor.getOriginalAppointment();
        }

        if (orderedIds.isEmpty()) {
            return Collections.emptyList();
        }

        Collections.reverse(orderedIds);

        Map<Long, MedicalRecord> existingRecords = new HashMap<>();
        for (Long priorAppointmentId : orderedIds) {
            medicalRecordRepository.findByAppointment_Id(priorAppointmentId)
                    .ifPresent(record -> {
                        touchMedicalRecord(record);
                        existingRecords.put(priorAppointmentId, record);
                    });
        }

        Map<Long, BillingNote> billingNotes = new HashMap<>();
        for (Long priorAppointmentId : orderedIds) {
            billingNoteRepository.findByAppointment_Id(priorAppointmentId)
                    .ifPresent(note -> {
                        touchBillingNote(note);
                        billingNotes.put(priorAppointmentId, note);
                    });
        }

        List<ReexamHistoryStepView> history = new ArrayList<>();
        for (Long priorAppointmentId : orderedIds) {
            Appointment priorAppointment = priorAppointments.get(priorAppointmentId);
            if (priorAppointment == null) {
                continue;
            }

            history.add(toHistoryStep(
                    priorAppointment,
                    existingRecords.get(priorAppointmentId),
                    billingNotes.get(priorAppointmentId)
            ));
        }

        return history;
    }

    private ReexamHistoryStepView toHistoryStep(Appointment appointment,
                                                MedicalRecord medicalRecord,
                                                BillingNote billingNote) {
        return new ReexamHistoryStepView(
                appointment.getId(),
                appointment.getDate(),
                appointment.getStartTime(),
                appointment.getEndTime(),
                appointment.getStatus(),
                buildServiceLabel(appointment),
                medicalRecord == null ? null : medicalRecord.getDiagnosis(),
                medicalRecord == null ? null : medicalRecord.getComplaintNote(),
                medicalRecord == null ? null : medicalRecord.getClinicalNotes(),
                medicalRecord == null ? List.of() : medicalRecord.getFindings().stream()
                        .map(finding -> new FindingView(
                                finding.getToothNo(),
                                finding.getCondition(),
                                finding.getSeverity(),
                                finding.getNote()
                        ))
                        .toList(),
                medicalRecord == null ? List.of() : medicalRecord.getProposedServices().stream()
                        .map(ps -> ps.getService() != null ? ps.getService().getName() : null)
                        .filter(name -> name != null && !name.isBlank())
                        .distinct()
                        .toList(),
                medicalRecord == null ? List.of() : medicalRecord.getImages().stream()
                        .map(image -> new ImageView(
                                image.getType(),
                                image.getNote(),
                                image.getUrl()
                        ))
                        .toList(),
                billingNote == null ? List.of() : billingNote.getPrescriptionItems().stream()
                        .map(item -> new PrescriptionView(
                                item.getMedicineName(),
                                item.getDosage(),
                                item.getNote()
                        ))
                        .toList(),
                billingNote == null ? List.of() : billingNote.getPerformedServices().stream()
                        .map(service -> new PerformedServiceView(
                                service.getService() != null ? service.getService().getName() : null,
                                service.getQty(),
                                service.getToothNo()
                        ))
                        .toList()
        );
    }

    private void touchMedicalRecord(MedicalRecord medicalRecord) {
        if (medicalRecord == null) {
            return;
        }
        for (MedicalFinding finding : medicalRecord.getFindings()) {
            finding.getToothNo();
            finding.getCondition();
            finding.getSeverity();
            finding.getNote();
        }
        for (MedicalImage image : medicalRecord.getImages()) {
            image.getType();
            image.getNote();
            image.getUrl();
        }
        medicalRecord.getProposedServices().forEach(ps -> {
            if (ps.getService() != null) {
                ps.getService().getName();
            }
        });
    }

    private void touchBillingNote(BillingNote billingNote) {
        if (billingNote == null) {
            return;
        }
        for (BillingPrescriptionItem item : billingNote.getPrescriptionItems()) {
            item.getMedicineName();
            item.getDosage();
            item.getNote();
        }
        for (BillingPerformedService service : billingNote.getPerformedServices()) {
            service.getQty();
            service.getToothNo();
            if (service.getService() != null) {
                service.getService().getName();
            }
        }
    }

    private String buildServiceLabel(Appointment appointment) {
        if (appointment == null) {
            return "";
        }

        List<AppointmentDetail> details = appointment.getAppointmentDetails();
        if (details != null && !details.isEmpty()) {
            String joined = details.stream()
                    .map(detail -> {
                        String name = detail.getServiceNameSnapshot();
                        if ((name == null || name.isBlank()) && detail.getService() != null) {
                            name = detail.getService().getName();
                        }
                        return name;
                    })
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .reduce((left, right) -> left + ", " + right)
                    .orElse("");

            if (!joined.isBlank()) {
                return joined;
            }
        }

        if (appointment.getService() != null && appointment.getService().getName() != null) {
            return appointment.getService().getName();
        }

        return "";
    }

    public record ReexamHistoryStepView(
            Long appointmentId,
            java.time.LocalDate date,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            AppointmentStatus status,
            String serviceLabel,
            String diagnosis,
            String complaintNote,
            String clinicalNotes,
            List<FindingView> findings,
            List<String> proposedServices,
            List<ImageView> images,
            List<PrescriptionView> prescriptions,
            List<PerformedServiceView> performedServices
    ) {}

    public record FindingView(
            String toothNo,
            String condition,
            String severity,
            String note
    ) {}

    public record ImageView(
            String type,
            String note,
            String url
    ) {}

    public record PrescriptionView(
            String medicineName,
            String dosage,
            String note
    ) {}

    public record PerformedServiceView(
            String serviceName,
            int qty,
            String toothNo
    ) {}

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
                if (i.getUrl() == null || i.getUrl().isBlank()) {
                    continue;
                }
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

