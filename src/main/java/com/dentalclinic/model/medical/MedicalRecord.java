package com.dentalclinic.model.medical;

import com.dentalclinic.model.appointment.Appointment;
import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "medical_record")
public class MedicalRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(name = "diagnosis", columnDefinition = "NVARCHAR(MAX)")
    private String diagnosis;

    @Column(name = "treatment_note", columnDefinition = "NVARCHAR(MAX)")
    private String treatmentNote;

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL)
    private List<Prescription> prescriptions;

    // GETTER / SETTER
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public String getTreatmentNote() { return treatmentNote; }
    public void setTreatmentNote(String treatmentNote) { this.treatmentNote = treatmentNote; }
    public List<Prescription> getPrescriptions() { return prescriptions; }
    public void setPrescriptions(List<Prescription> prescriptions) { this.prescriptions = prescriptions; }
}
