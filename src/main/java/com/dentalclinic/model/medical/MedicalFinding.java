package com.dentalclinic.model.medical;

import jakarta.persistence.*;

@Entity
@Table(name = "medical_finding")
public class MedicalFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medical_record_id")
    private MedicalRecord medicalRecord;

    @Column(name = "tooth_no", length = 50)
    private String toothNo;

    @Column(name = "condition", length = 255)
    private String condition;

    @Column(length = 50)
    private String severity;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String note;

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MedicalRecord getMedicalRecord() { return medicalRecord; }
    public void setMedicalRecord(MedicalRecord medicalRecord) { this.medicalRecord = medicalRecord; }

    public String getToothNo() { return toothNo; }
    public void setToothNo(String toothNo) { this.toothNo = toothNo; }

    public String getCondition() { return condition; }
    public void setCondition(String condition) { this.condition = condition; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}