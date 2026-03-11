package com.dentalclinic.dto.customer;

public class AppointmentPrescriptionItemDto {
    private Long id;
    private String medicineName;
    private String dosage;
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

    public String getDosage() { return dosage; }
    public void setDosage(String dosage) { this.dosage = dosage; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
