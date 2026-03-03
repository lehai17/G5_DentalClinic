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

    @Column(name = "secondary_diagnosis", columnDefinition = "NVARCHAR(MAX)")
    private String secondaryDiagnosis;

    @Column(name = "complaint_code", length = 50)
    private String complaintCode;

    @Column(name = "complaint_note", columnDefinition = "NVARCHAR(MAX)")
    private String complaintNote;

    @Column(name = "clinical_notes", columnDefinition = "NVARCHAR(MAX)")
    private String clinicalNotes;

    // relational children replacing previous treatmentNote JSON
    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<MedicalFinding> findings = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<MedicalImage> images = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<MedicalProposedService> proposedServices = new java.util.ArrayList<>();

    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL)
    private List<Prescription> prescriptions;

    // GETTER / SETTER
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public java.util.List<MedicalFinding> getFindings() { return findings; }
    public void setFindings(java.util.List<MedicalFinding> findings) { this.findings = findings; }

    public java.util.List<MedicalImage> getImages() { return images; }
    public void setImages(java.util.List<MedicalImage> images) { this.images = images; }

    public java.util.List<MedicalProposedService> getProposedServices() { return proposedServices; }
    public void setProposedServices(java.util.List<MedicalProposedService> proposedServices) { this.proposedServices = proposedServices; }

    public String getSecondaryDiagnosis() { return secondaryDiagnosis; }
    public void setSecondaryDiagnosis(String secondaryDiagnosis) { this.secondaryDiagnosis = secondaryDiagnosis; }

    public String getComplaintCode() { return complaintCode; }
    public void setComplaintCode(String complaintCode) { this.complaintCode = complaintCode; }

    public String getComplaintNote() { return complaintNote; }
    public void setComplaintNote(String complaintNote) { this.complaintNote = complaintNote; }

    public String getClinicalNotes() { return clinicalNotes; }
    public void setClinicalNotes(String clinicalNotes) { this.clinicalNotes = clinicalNotes; }

    public List<Prescription> getPrescriptions() { return prescriptions; }
    public void setPrescriptions(List<Prescription> prescriptions) { this.prescriptions = prescriptions; }
}
