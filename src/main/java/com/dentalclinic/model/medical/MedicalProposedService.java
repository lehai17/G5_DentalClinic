package com.dentalclinic.model.medical;

import com.dentalclinic.model.service.Services;
import jakarta.persistence.*;

@Entity
@Table(name = "medical_proposed_service")
public class MedicalProposedService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "medical_record_id")
    private MedicalRecord medicalRecord;

    @ManyToOne
    @JoinColumn(name = "service_id")
    private Services service;

    @Column(nullable = false)
    private int qty = 1;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public MedicalRecord getMedicalRecord() { return medicalRecord; }
    public void setMedicalRecord(MedicalRecord medicalRecord) { this.medicalRecord = medicalRecord; }

    public Services getService() { return service; }
    public void setService(Services service) { this.service = service; }

    public int getQty() { return qty; }
    public void setQty(int qty) { this.qty = qty; }
}