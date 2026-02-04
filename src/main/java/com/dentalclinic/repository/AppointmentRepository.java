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
        // true nếu bác sĩ đã có lịch trùng khung giờ (bất kỳ appointment nào overlap)
        return countBusyAppointments(dentistId, date, startTime, endTime) > 0;
    }

    /** Không cho phép 1 slot (DentistSchedule) có nhiều appointment còn hiệu lực (không tính CANCELLED). */
    boolean existsBySlot_IdAndStatusNot(Long slotId, AppointmentStatus status);

    // (Giữ lại nếu chỗ khác trong project đang gọi; không dùng cho rule mới)
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

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer c
        JOIN FETCH c.user cu
        JOIN FETCH a.service s
        JOIN FETCH a.dentist d
        WHERE d.id = :dentistProfileId
          AND a.date BETWEEN :start AND :end
              AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED
    """)
    List<Appointment> findScheduleForWeek(
            @Param("dentistProfileId") Long dentistProfileId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end
    );

    List<Appointment> findByCustomer_FullNameContainingIgnoreCase(String keyword);

}
