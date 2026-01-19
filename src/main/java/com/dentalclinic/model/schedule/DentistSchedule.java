package com.dentalclinic.model.schedule;

import com.dentalclinic.model.profile.DentistProfile;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "dentist_schedule")
public class DentistSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private DentistProfile dentist;

    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
}
