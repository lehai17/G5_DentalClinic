package com.dentalclinic.model.schedule;

import jakarta.persistence.*;

@Entity
@Table(name = "clinic_working_hour")
public class ClinicWorkingHour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int dayOfWeek;
    private String openTime;
    private String closeTime;
}
