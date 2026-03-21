package com.dentalclinic.repository;

import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.model.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface DentistScheduleRepository extends JpaRepository<DentistSchedule, Long> {

    List<DentistSchedule> findByDate(LocalDate date);

    List<DentistSchedule> findByDentist_IdAndDate(Long dentistId, LocalDate date);

    List<DentistSchedule> findByDentist_User_IdAndDate(Long dentistUserId, LocalDate date);

    @Query("""
            SELECT ds
            FROM DentistSchedule ds
            JOIN FETCH ds.dentist d
            JOIN FETCH d.user u
            WHERE ds.available = true
              AND u.status = :activeStatus
              AND (
                    ds.date = :targetDate
                    OR (ds.date IS NULL AND ds.dayOfWeek = :dayOfWeek)
                  )
            """)
    List<DentistSchedule> findEffectiveSchedulesForDate(@Param("targetDate") LocalDate targetDate,
                                                        @Param("dayOfWeek") DayOfWeek dayOfWeek,
                                                        @Param("activeStatus") UserStatus activeStatus);
}
