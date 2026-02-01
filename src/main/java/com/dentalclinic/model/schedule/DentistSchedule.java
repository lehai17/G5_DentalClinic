package com.dentalclinic.model.schedule;

import com.dentalclinic.model.profile.DentistProfile;
import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "dentist_schedule")
public class DentistSchedule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "dentist_id")
    private DentistProfile dentist;
g//gg
//    @Column(name = "work_date")
//    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week")
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

//    @Column(name = "is_available")
//    private boolean available = true;
}
