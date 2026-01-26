package com.dentalclinic.repository;

import com.dentalclinic.model.schedule.DentistSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DentistScheduleRepository extends JpaRepository<DentistSchedule, Long> {

    // View available time slots
    List<DentistSchedule> findByDate(LocalDate date);

    List<DentistSchedule> findByDentistUserIdAndDate(
            Long dentistUserId,
            LocalDate date
    );
}
