package com.dentalclinic.repository;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    @Query(
            value = """
        SELECT CASE WHEN COUNT(*) > 0 THEN 1 ELSE 0 END
        FROM appointment
        WHERE dentist_id = :dentistId
          AND appointment_date = :date
          AND start_time < CAST(:endTime AS time)
          AND end_time > CAST(:startTime AS time)
        """,
            nativeQuery = true
    )
    int countBusyAppointments(
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
        return countBusyAppointments(dentistId, date, startTime, endTime) > 0;
    }

    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM Appointment a
        WHERE a.dentist.id = :dentistId
          AND a.date = :date
          AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED
          AND a.startTime < :endTime
          AND a.endTime > :startTime
        """)
    boolean hasOverlappingAppointment(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    @Query("""
        SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
        FROM Appointment a
        WHERE a.dentist.id = :dentistId
          AND a.date = :date
          AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED
          AND a.id <> :appointmentId
          AND a.startTime < :endTime
          AND a.endTime > :startTime
        """)
    boolean hasOverlappingAppointmentExcludingSelf(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("appointmentId") Long appointmentId
    );

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.dentist.id = :dentistId
AND a.date = :date
          AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED
          AND a.startTime < :endTime
          AND a.endTime > :startTime
        """)
    List<Appointment> findOverlappingAppointments(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime
    );

    boolean existsBySlot_IdAndStatusNot(Long slotId, AppointmentStatus status);

    boolean existsBySlot_Id(Long slotId);

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

    @Query(
            value = """
            SELECT CASE WHEN COUNT(*) > 0 THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END
            FROM appointment a
            WHERE a.customer_id = :userId
              AND a.status IN (:activeStatuses)
              AND a.appointment_date = :date
              AND a.start_time < CAST(:endTime AS time)
              AND a.end_time > CAST(:startTime AS time)
            """,
            nativeQuery = true
    )
    boolean existsCustomerOverlap(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("activeStatuses") List<String> activeStatuses
    );

    @Query(
            value = """
            SELECT CASE WHEN COUNT(*) > 0 THEN CAST(1 AS BIT) ELSE CAST(0 AS BIT) END
            FROM appointment a
            WHERE a.customer_id = :userId
              AND a.id <> :excludeAppointmentId
              AND a.status IN (:activeStatuses)
              AND a.appointment_date = :date
              AND a.start_time < CAST(:endTime AS time)
              AND a.end_time > CAST(:startTime AS time)
            """,
            nativeQuery = true
    )
    boolean existsCustomerOverlapExcludingAppointment(
            @Param("userId") Long userId,
            @Param("excludeAppointmentId") Long excludeAppointmentId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("activeStatuses") List<String> activeStatuses
    );

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer c
        JOIN FETCH c.user cu
        JOIN FETCH a.service s
        LEFT JOIN FETCH a.dentist d
        WHERE d.id = :dentistProfileId
          AND a.date BETWEEN :start AND :end
          AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED
    """)
    List<Appointment> findScheduleForWeek(
            @Param("dentistProfileId") Long dentistProfileId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    Page<Appointment> findByCustomer_FullNameContainingIgnoreCase(String keyword, Pageable pageable);

    @Query(
            value = """
    SELECT COUNT(*)
    FROM appointment
    WHERE dentist_id = :dentistId
      AND appointment_date = :date
      AND id <> :appointmentId
      AND start_time < CAST(:endTime AS time)
      AND end_time > CAST(:startTime AS time)
    """,
            nativeQuery = true
    )
    int countBusyAppointmentsExcludeSelf(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("appointmentId") Long appointmentId
    );

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByDate(LocalDate date);

    @Query("""
        SELECT a FROM Appointment a
        WHERE a.date = :date
          AND a.status IN :statuses
        """)
    List<Appointment> findByDateAndStatusIn(
            @Param("date") LocalDate date,
            @Param("statuses") List<AppointmentStatus> statuses
    );

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer c
        JOIN FETCH c.user cu
        JOIN FETCH a.service s
        LEFT JOIN FETCH a.dentist d
        WHERE a.id = :appointmentId
    """)
    Optional<Appointment> findByIdWithDetails(@Param("appointmentId") Long appointmentId);

    @Query("""
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.appointmentSlots ass
        LEFT JOIN FETCH ass.slot
        WHERE a.id = :appointmentId
    """)
    Optional<Appointment> findByIdWithSlots(@Param("appointmentId") Long appointmentId);

    @Query("""
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.appointmentSlots ass
        LEFT JOIN FETCH ass.slot
        WHERE a.id = :appointmentId
          AND a.customer.user.id = :userId
    """)
    Optional<Appointment> findByIdWithSlotsAndCustomerUserId(
            @Param("appointmentId") Long appointmentId,
            @Param("userId") Long userId
    );

    // Methods merged from AppointmentSlotRepository - using @Query for custom operations
    @Query("SELECT aslot FROM AppointmentSlot aslot WHERE aslot.appointment.id = :appointmentId ORDER BY aslot.slotOrder ASC")
    List<Object[]> findAppointmentSlotDetailsByAppointmentId(@Param("appointmentId") Long appointmentId);

    @Modifying
    @Query("DELETE FROM AppointmentSlot aslot WHERE aslot.appointment.id = :appointmentId")
    void deleteAppointmentSlotsByAppointmentId(@Param("appointmentId") Long appointmentId);
}