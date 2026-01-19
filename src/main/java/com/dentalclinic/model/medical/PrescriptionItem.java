package com.dentalclinic.model.medical;

import jakarta.persistence.*;

@Entity
@Table(name = "prescription_item")
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Prescription prescription;

    private String medicineName;
    private String dosage;
    private String instruction;
}
