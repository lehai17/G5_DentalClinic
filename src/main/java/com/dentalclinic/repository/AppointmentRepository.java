package com.dentalclinic.repository;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // =========================
    // Check dentist busy (SQL Server: cast time params to TIME to avoid time/datetime mismatch)
    // =========================
    @Query(value = "SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM appointment a WHERE a.dentist_id = :dentistId AND a.appointment_date = :date AND a.start_time <= CAST(:startTime AS TIME) AND a.end_time >= CAST(:endTime AS TIME)) THEN 1 ELSE 0 END AS BIT)", nativeQuery = true)
    boolean hasOverlappingAppointments(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    default boolean existsByDentist_IdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            Long dentistId,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime
    ) {
        return hasOverlappingAppointments(dentistId, date, startTime, endTime);
    }

    Optional<Appointment> findByIdAndCustomer_User_Id(
            Long appointmentId,
            Long customerUserId
    );

    List<Appointment> findByCustomer_User_IdAndStatus(
            Long customerUserId,
            AppointmentStatus status
    );

    List<Appointment> findByCustomer_User_IdOrderByDateDesc(
            Long customerUserId
    );
    List<Appointment> findByCustomerId(Long customerId);
}
