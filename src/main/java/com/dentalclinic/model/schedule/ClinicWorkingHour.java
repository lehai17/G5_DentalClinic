package com.dentalclinic.model.schedule;

import jakarta.persistence.*;

@Entity
@Table(name = "clinic_working_hour")
public class ClinicWorkingHour {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "day_of_week")
    private int dayOfWeek;

    @Column(name = "open_time")
    private String openTime;

    @Column(name = "close_time")
    private String closeTime;
}
