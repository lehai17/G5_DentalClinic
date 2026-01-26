package com.dentalclinic.model.medical;

import jakarta.persistence.*;

@Entity
@Table(name = "prescription_item")
class PrescriptionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "prescription_id")
    private Prescription prescription;

    @Column(name = "medicine_name", columnDefinition = "NVARCHAR(255)")
    private String medicineName;

    @Column(columnDefinition = "NVARCHAR(100)")
    private String dosage;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String instruction;

}
