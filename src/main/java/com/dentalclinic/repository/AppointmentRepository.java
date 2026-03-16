package com.dentalclinic.repository;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    List<Appointment> findByCustomer_FullNameContainingIgnoreCase(String keyword);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.dentist.id = :dentistId AND a.date >= :currentDate AND a.status NOT IN :excludedStatuses")
    int countUpcomingAppointments(@Param("dentistId") Long dentistId, @Param("currentDate") LocalDate currentDate,
            @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses);

    @Query("""
                SELECT a FROM Appointment a
                WHERE a.customer.id = :customerId
                AND (
                    a.status IN (com.dentalclinic.model.appointment.AppointmentStatus.COMPLETED, com.dentalclinic.model.appointment.AppointmentStatus.DONE, com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED)
                    OR (a.date < CAST(:now AS date) OR (a.date = CAST(:now AS date) AND a.startTime <= CAST(:now AS time)))
                )
                ORDER BY a.date DESC, a.startTime DESC
            """)
    List<Appointment> findCompletedAppointmentsByCustomerId(@Param("customerId") Long customerId,
            @Param("now") java.time.LocalDateTime now);

    @Query("""
                SELECT a FROM Appointment a
                WHERE a.customer.id = :customerId
                AND a.status IN (com.dentalclinic.model.appointment.AppointmentStatus.PENDING, com.dentalclinic.model.appointment.AppointmentStatus.PENDING_DEPOSIT, com.dentalclinic.model.appointment.AppointmentStatus.CONFIRMED, com.dentalclinic.model.appointment.AppointmentStatus.CHECKED_IN, com.dentalclinic.model.appointment.AppointmentStatus.IN_PROGRESS, com.dentalclinic.model.appointment.AppointmentStatus.EXAMINING, com.dentalclinic.model.appointment.AppointmentStatus.REEXAM, com.dentalclinic.model.appointment.AppointmentStatus.WAITING_PAYMENT)
                AND (a.date > CAST(:now AS date) OR (a.date = CAST(:now AS date) AND a.startTime > CAST(:now AS time)))
                ORDER BY a.date ASC, a.startTime ASC
            """)
    List<Appointment> findUpcomingAppointmentsByCustomerId(@Param("customerId") Long customerId,
            @Param("now") java.time.LocalDateTime now);

    @Query(value = """
            SELECT COUNT(*) FROM appointment
            WHERE dentist_id = :dentistId
              AND appointment_date = :date
              AND start_time < CAST(:endTime AS TIME)
              AND end_time > CAST(:startTime AS TIME)
            """, nativeQuery = true)
    int countBusyAppointments(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    @Query(value = """
            SELECT COUNT(*) FROM appointment
            WHERE dentist_id = :dentistId
              AND appointment_date = :date
              AND status <> 'CANCELLED'
              AND start_time < CAST(:endTime AS TIME)
              AND end_time > CAST(:startTime AS TIME)
            """, nativeQuery = true)
    int checkOverlappingAppointment(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime);

    @Query(value = """
            SELECT COUNT(*) FROM appointment
            WHERE dentist_id = :dentistId
              AND appointment_date = :date
              AND status <> 'CANCELLED'
              AND id <> :appointmentId
              AND start_time < CAST(:endTime AS TIME)
              AND end_time > CAST(:startTime AS TIME)
            """, nativeQuery = true)
    int checkOverlappingAppointmentExcludingSelf(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("appointmentId") Long appointmentId);

    @Query(value = """
            SELECT COUNT(*) FROM appointment
            WHERE customer_id = :userId
              AND status IN (:activeStatuses)
              AND appointment_date = :date
              AND start_time < CAST(:endTime AS TIME)
              AND end_time > CAST(:startTime AS TIME)
            """, nativeQuery = true)
    int checkCustomerOverlap(
            @Param("userId") Long userId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("activeStatuses") List<String> activeStatuses);

    @Query(value = """
            SELECT COUNT(*) FROM appointment
            WHERE customer_id = :userId
              AND id <> :excludeAppointmentId
              AND status IN (:activeStatuses)
              AND appointment_date = :date
              AND start_time < CAST(:endTime AS TIME)
              AND end_time > CAST(:startTime AS TIME)
            """, nativeQuery = true)
    int checkCustomerOverlapExcludingAppointment(
            @Param("userId") Long userId,
            @Param("excludeAppointmentId") Long excludeAppointmentId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("activeStatuses") List<String> activeStatuses);

    default boolean hasOverlappingAppointment(Long dentistId, LocalDate date, LocalTime startTime,
            LocalTime endTime) {
        return checkOverlappingAppointment(dentistId, date, startTime, endTime) > 0;
    }

    default boolean hasOverlappingAppointmentExcludingSelf(Long dentistId, LocalDate date, LocalTime startTime,
            LocalTime endTime, Long appointmentId) {
        return checkOverlappingAppointmentExcludingSelf(dentistId, date, startTime, endTime, appointmentId) > 0;
    }

    default boolean existsCustomerOverlap(Long userId, LocalDate date, LocalTime startTime, LocalTime endTime,
            List<String> activeStatuses) {
        return checkCustomerOverlap(userId, date, startTime, endTime, activeStatuses) > 0;
    }

    default boolean existsCustomerOverlapExcludingAppointment(Long userId, Long excludeId, LocalDate date,
            LocalTime startTime, LocalTime endTime, List<String> activeStatuses) {
        return checkCustomerOverlapExcludingAppointment(userId, excludeId, date, startTime, endTime,
                activeStatuses) > 0;
    }

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer c
        JOIN FETCH c.user cu
        JOIN FETCH a.service s
        LEFT JOIN FETCH a.appointmentDetails ad
        LEFT JOIN FETCH a.dentist d
        WHERE d.id = :dentistProfileId
          AND a.date BETWEEN :start AND :end
          AND a.status IN(
              com.dentalclinic.model.appointment.AppointmentStatus.EXAMINING,
                  com.dentalclinic.model.appointment.AppointmentStatus.CONFIRMED,
                      com.dentalclinic.model.appointment.AppointmentStatus.CHECKED_IN,
                          com.dentalclinic.model.appointment.AppointmentStatus.COMPLETED,
                              com.dentalclinic.model.appointment.AppointmentStatus.DONE,
                  com.dentalclinic.model.appointment.AppointmentStatus.WAITING_PAYMENT
              ) 
    """)
    List<Appointment> findScheduleForWeek(@Param("dentistProfileId") Long dentistProfileId,
                                          @Param("start") LocalDate start,
                                          @Param("end") LocalDate end);

    @Query("""
        SELECT DISTINCT a FROM Appointment a
        JOIN FETCH a.customer c
        JOIN FETCH c.user cu
        JOIN FETCH a.service s
        LEFT JOIN FETCH a.appointmentDetails ad
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

    @Query("SELECT aslot FROM AppointmentSlot aslot WHERE aslot.appointment.id = :appointmentId ORDER BY aslot.slotOrder ASC")
    List<Object[]> findAppointmentSlotDetailsByAppointmentId(@Param("appointmentId") Long appointmentId);

    @Query("""
                SELECT a FROM Appointment a
                LEFT JOIN FETCH a.appointmentSlots ass
                LEFT JOIN FETCH ass.slot
                WHERE a.id = :appointmentId
                  AND a.customer.user.id = :userId
            """)
    Optional<Appointment> findByIdWithSlotsAndCustomerUserId(
            @Param("appointmentId") Long appointmentId,
            @Param("userId") Long userId);
        SELECT COUNT(a) FROM Appointment a
        WHERE a.dentist.id = :dentistId
          AND a.date = :date
          AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED
    """)
    long countTotalByDentistAndDate(@Param("dentistId") Long dentistId, @Param("date") LocalDate date);

    /**
     * Dashboard statistic: treat any appointment that is "done" or "completed"
     * as finished for the purposes of the "completed today" counter.
     */
    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.dentist.id = :dentistId
          AND a.date = :date
          AND a.status IN (
              com.dentalclinic.model.appointment.AppointmentStatus.COMPLETED,
              com.dentalclinic.model.appointment.AppointmentStatus.DONE,
              com.dentalclinic.model.appointment.AppointmentStatus.WAITING_PAYMENT
          )
    """)
    long countCompletedByDentistAndDate(@Param("dentistId") Long dentistId, @Param("date") LocalDate date);

    @Query("""
        SELECT a.date, a.status, COUNT(a)
        FROM Appointment a
        WHERE a.dentist.id = :dentistId
          AND a.date BETWEEN :start AND :end
        GROUP BY a.date, a.status
    """)
    List<Object[]> countStatusByDentistAndDateRange(@Param("dentistId") Long dentistId,
                                                    @Param("start") LocalDate start,
                                                    @Param("end") LocalDate end);

    @EntityGraph(attributePaths = {"customer", "customer.user", "appointmentDetails", "appointmentDetails.service", "service", "dentist"})
    @Query("""
        SELECT a
        FROM Appointment a
        WHERE a.dentist.id = :dentistId
          AND a.status IN :statuses
          AND a.date >= :today
        ORDER BY a.date ASC, a.startTime ASC
    """)
    List<Appointment> findUpcomingForDentist(@Param("dentistId") Long dentistId,
                                             @Param("statuses") List<AppointmentStatus> statuses,
                                             @Param("today") LocalDate today,
                                             Pageable pageable);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.dentist.id = :dentistId AND a.date >= :currentDate AND a.status NOT IN :excludedStatuses")
    int countUpcomingAppointments(@Param("dentistId") Long dentistId,
                                  @Param("currentDate") LocalDate currentDate,
                                  @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses);

    @Query("""
                SELECT COUNT(a)
                FROM Appointment a
                WHERE a.dentist.id = :dentistId
                  AND a.date = :date
                  AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED
            """)
    long countTotalByDentistAndDate(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date);

    @Query("""
                SELECT COUNT(a)
                FROM Appointment a
                WHERE a.dentist.id = :dentistId
                  AND a.date = :date
                  AND a.status IN (
                        com.dentalclinic.model.appointment.AppointmentStatus.DONE,
                        com.dentalclinic.model.appointment.AppointmentStatus.COMPLETED
                  )
            """)
    long countCompletedByDentistAndDate(
            @Param("dentistId") Long dentistId,
            @Param("date") LocalDate date);
    @EntityGraph(attributePaths = {"customer", "customer.user", "appointmentDetails", "appointmentDetails.service", "service", "dentist"})
    List<Appointment> findByDentist_IdAndStatusIn(Long dentistId, List<AppointmentStatus> statuses);

    @Query("SELECT a FROM Appointment a WHERE a.customer.id = :customerId AND a.status = 'COMPLETED' ORDER BY a.date DESC, a.startTime DESC")
    List<Appointment> findCompletedAppointmentsByCustomerId(@Param("customerId") Long customerId);

    boolean existsBySlot_IdAndStatusNot(Long slotId, AppointmentStatus status);

    boolean existsBySlot_Id(Long slotId);

    Optional<Appointment> findByIdAndCustomer_User_Id(Long appointmentId, Long customerUserId);

    List<Appointment> findByCustomer_User_IdAndStatus(Long customerUserId, AppointmentStatus status);

    List<Appointment> findByCustomer_User_IdOrderByDateDesc(Long customerUserId);

    @EntityGraph(attributePaths = {"service", "appointmentDetails", "appointmentDetails.service"})
    List<Appointment> findByCustomer_User_IdAndCustomerHiddenFalseOrderByDateDesc(Long customerUserId);

    @EntityGraph(attributePaths = {"service", "appointmentDetails", "appointmentDetails.service"})
    List<Appointment> findByCustomer_User_IdAndStatusOrderByDateDescStartTimeDesc(Long customerUserId,
                                                                                   AppointmentStatus status);

    @EntityGraph(attributePaths = {"service", "appointmentDetails", "appointmentDetails.service"})
    List<Appointment> findByCustomer_User_IdAndDateAndStatusNotOrderByStartTimeAsc(Long customerUserId,
                                                                                    LocalDate date,
                                                                                    AppointmentStatus excludedStatus);

    Page<Appointment> findByCustomer_User_Id(Long userId, Pageable pageable);

    List<Appointment> findByCustomerId(Long customerId);

    Page<Appointment> findByCustomer_FullNameContainingIgnoreCase(String keyword, Pageable pageable);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByDate(LocalDate date);

    Page<Appointment> findByCustomer_FullNameContainingIgnoreCaseAndService_NameContainingIgnoreCase(
            String customerKeyword, String serviceKeyword, Pageable pageable);

    @Modifying
    @Query("DELETE FROM AppointmentSlot aslot WHERE aslot.appointment.id = :appointmentId")
    void deleteAppointmentSlotsByAppointmentId(@Param("appointmentId") Long appointmentId);

    default boolean existsByDentist_IdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            Long dentistId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        return countBusyAppointments(dentistId, date, startTime, endTime) > 0;
    }

    Page<Appointment> findByCustomer_User_Id(Long userId, PageRequest pageable);

    @Query("SELECT a FROM Appointment a " +
            "JOIN FETCH a.customer c " +
            "JOIN FETCH c.user cu " +
            "JOIN FETCH a.service s " +
            "LEFT JOIN FETCH a.dentist d " +
            "WHERE a.date = :date AND a.status IN :statuses " +
            "AND (:dentistId IS NULL OR d.id = :dentistId) " +
            "ORDER BY a.startTime ASC")
    List<Appointment> findAgendaWithDetails(
            @Param("date") LocalDate date,
            @Param("statuses") List<AppointmentStatus> statuses,
            @Param("dentistId") Long dentistId);

    @Query("SELECT a FROM Appointment a WHERE a.date = :date AND a.status IN :statuses")
    List<Appointment> findByDateAndStatusIn(
            @Param("date") LocalDate date,
            @Param("statuses") List<AppointmentStatus> statuses);

    @Query("SELECT a FROM Appointment a WHERE a.date BETWEEN :startDate AND :endDate AND a.status IN :statuses")
    List<Appointment> findByDateBetweenAndStatusIn(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("statuses") List<AppointmentStatus> statuses);

    long countByDateAndStatusIn(LocalDate date, List<AppointmentStatus> statuses);

    long countByStatusIn(List<AppointmentStatus> statuses);

    Page<Appointment> findByService_NameContainingIgnoreCase(String serviceKeyword, Pageable pageable);

    @Query("SELECT a FROM Appointment a WHERE a.originalAppointment.id = :originalId")
    Optional<Appointment> findReexamByOriginalAppointmentId(@Param("originalId") Long originalId);

    @Modifying
    @Query("UPDATE Appointment a SET a.status = 'CONFIRMED' WHERE a.originalAppointment.id = :originalId AND a.status = 'REEXAM'")
    int updateReexamStatusToConfirmed(@Param("originalId") Long originalId);

    @Query("SELECT a.id, a.originalAppointment.id, a.status FROM Appointment a WHERE a.originalAppointment.id = :originalId")
    List<Object[]> debugFindReexamByOriginalId(@Param("originalId") Long originalId);

    List<Appointment> findByCustomer_IdAndDateAndStatusOrderByCreatedAtDesc(Long customerId, LocalDate date,
            AppointmentStatus status);

    List<Appointment> findByCustomer_User_IdAndDateAndStatusNotOrderByStartTimeAsc(Long userId, LocalDate date,
            AppointmentStatus status);

    List<Appointment> findAllByStatusAndCreatedAtBefore(AppointmentStatus status, java.time.LocalDateTime time);
}
