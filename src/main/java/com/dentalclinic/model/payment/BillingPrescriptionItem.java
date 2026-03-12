package com.dentalclinic.model.payment;

import jakarta.persistence.*;

@Entity
@Table(name = "billing_prescription_item")
public class BillingPrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "billing_note_id")
    private BillingNote billingNote;

    @Column(name = "medicine_name", length = 255)
    private String medicineName;

    @Column(length = 100)
    private String dosage;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String note;

    // getters & setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BillingNote getBillingNote() { return billingNote; }
    public void setBillingNote(BillingNote billingNote) { this.billingNote = billingNote; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}