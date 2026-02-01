package com.dentalclinic.controller.dentist;

public class MedicalRecordUpsertRequest {

    private String diagnosis;
    private String treatmentNote;

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getTreatmentNote() {
        return treatmentNote;
    }

    public void setTreatmentNote(String treatmentNote) {
        this.treatmentNote = treatmentNote;
    }
}
