package com.dentalclinic.repository;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    // =========================================================
    // 1. OVERLAP & AVAILABILITY QUERIES (NATIVE)
    // =========================================================

    @Query(value = """
        SELECT COUNT(*) FROM appointment
        WHERE dentist_id = :dentistId
          AND appointment_date = :date
          AND status <> 'CANCELLED'
          AND start_time < CAST(:endTime AS TIME)
          AND end_time > CAST(:startTime AS TIME)
        """, nativeQuery = true)
    int checkOverlappingAppointment(@Param("dentistId") Long dentistId,
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
    int checkOverlappingAppointmentExcludingSelf(@Param("dentistId") Long dentistId,
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
    int checkCustomerOverlap(@Param("userId") Long userId,
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
    int checkCustomerOverlapExcludingAppointment(@Param("userId") Long userId,
                                                 @Param("excludeAppointmentId") Long excludeAppointmentId,
                                                 @Param("date") LocalDate date,
                                                 @Param("startTime") LocalTime startTime,
                                                 @Param("endTime") LocalTime endTime,
                                                 @Param("activeStatuses") List<String> activeStatuses);

    // =========================================================
    // 2. HELPER DEFAULT METHODS
    // =========================================================

    default boolean hasOverlappingAppointment(Long dentistId, LocalDate date, LocalTime startTime, LocalTime endTime) {
        return checkOverlappingAppointment(dentistId, date, startTime, endTime) > 0;
    }

    default boolean hasOverlappingAppointmentExcludingSelf(Long dentistId, LocalDate date, LocalTime startTime, LocalTime endTime, Long appointmentId) {
        return checkOverlappingAppointmentExcludingSelf(dentistId, date, startTime, endTime, appointmentId) > 0;
    }

    default boolean existsCustomerOverlap(Long userId, LocalDate date, LocalTime startTime, LocalTime endTime, List<String> activeStatuses) {
        return checkCustomerOverlap(userId, date, startTime, endTime, activeStatuses) > 0;
    }

    default boolean existsCustomerOverlapExcludingAppointment(Long userId, Long excludeId, LocalDate date, LocalTime startTime, LocalTime endTime, List<String> activeStatuses) {
        return checkCustomerOverlapExcludingAppointment(userId, excludeId, date, startTime, endTime, activeStatuses) > 0;
    }

    // =========================================================
    // 3. FETCH WITH DETAILS (JPQL)
    // =========================================================

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
    List<Appointment> findScheduleForWeek(@Param("dentistProfileId") Long dentistProfileId,
                                          @Param("start") LocalDate start,
                                          @Param("end") LocalDate end);

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
    Optional<Appointment> findByIdWithSlotsAndCustomerUserId(@Param("appointmentId") Long appointmentId,
                                                             @Param("userId") Long userId);

    // =========================================================
    // 4. STATISTICS & COUNTING
    // =========================================================

    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.dentist.id = :dentistId
          AND a.date = :date
          AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED
    """)
    long countTotalByDentistAndDate(@Param("dentistId") Long dentistId, @Param("date") LocalDate date);

    @Query("""
        SELECT COUNT(a) FROM Appointment a
        WHERE a.dentist.id = :dentistId
          AND a.date = :date
          AND a.status = com.dentalclinic.model.appointment.AppointmentStatus.COMPLETED
    """)
    long countCompletedByDentistAndDate(@Param("dentistId") Long dentistId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.dentist.id = :dentistId AND a.date >= :currentDate AND a.status NOT IN :excludedStatuses")
    int countUpcomingAppointments(@Param("dentistId") Long dentistId,
                                  @Param("currentDate") LocalDate currentDate,
                                  @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses);

    // =========================================================
    // 5. CUSTOMER & STATUS QUERIES
    // =========================================================

    @Query("SELECT a FROM Appointment a WHERE a.customer.id = :customerId AND a.status = 'COMPLETED' ORDER BY a.date DESC, a.startTime DESC")
    List<Appointment> findCompletedAppointmentsByCustomerId(@Param("customerId") Long customerId);

    @Query("SELECT a FROM Appointment a WHERE a.customer.id = :customerId AND a.status IN ('PENDING', 'CONFIRMED') ORDER BY a.date ASC, a.startTime ASC")
    List<Appointment> findUpcomingAppointmentsByCustomerId(@Param("customerId") Long customerId);

    List<Appointment> findAllByStatusAndCreatedAtBefore(AppointmentStatus status, LocalDateTime createdAt);

    @EntityGraph(attributePaths = {"service", "appointmentDetails", "appointmentDetails.service"})
    Optional<Appointment> findByIdAndCustomer_User_Id(Long appointmentId, Long customerUserId);

    @EntityGraph(attributePaths = {"appointmentSlots", "appointmentDetails", "appointmentDetails.service"})
    Optional<Appointment> findByCustomer_IdAndDateAndStartTimeAndStatus(Long customerId,
                                                                        LocalDate date,
                                                                        LocalTime startTime,
                                                                        AppointmentStatus status);

    List<Appointment> findByCustomer_User_IdAndStatus(Long customerUserId, AppointmentStatus status);

    @EntityGraph(attributePaths = {"service", "appointmentDetails", "appointmentDetails.service"})
    List<Appointment> findByCustomer_User_IdOrderByDateDesc(Long customerUserId);

    Page<Appointment> findByCustomer_User_Id(Long userId, Pageable pageable);

    List<Appointment> findByCustomerId(Long customerId);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByDate(LocalDate date);

    List<Appointment> findByDateAndStatusIn(LocalDate date, List<AppointmentStatus> statuses);

    // =========================================================
    // 6. SEARCH & PAGINATION
    // =========================================================

    Page<Appointment> findByCustomer_FullNameContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Appointment> findByCustomer_FullNameContainingIgnoreCaseAndService_NameContainingIgnoreCase(String customerKeyword,
                                                                                                     String serviceKeyword,
                                                                                                     Pageable pageable);

    Page<Appointment> findByService_NameContainingIgnoreCase(String serviceKeyword, Pageable pageable);

    // =========================================================
    // 7. SLOT LOGIC
    // =========================================================

    boolean existsBySlot_IdAndStatusNot(Long slotId, AppointmentStatus status);

    boolean existsBySlot_Id(Long slotId);

    @Modifying
    @Query("DELETE FROM AppointmentSlot aslot WHERE aslot.appointment.id = :appointmentId")
    void deleteAppointmentSlotsByAppointmentId(@Param("appointmentId") Long appointmentId);

    // =========================================================
    // 8. RE-EXAM QUERIES
    // =========================================================

    Optional<Appointment> findByOriginalAppointment_IdAndStatus(Long originalAppointmentId, AppointmentStatus status);

    @Query("SELECT a FROM Appointment a WHERE a.originalAppointment.id = :originalAppointmentId")
    Optional<Appointment> findReexamByOriginalAppointmentId(@Param("originalAppointmentId") Long originalAppointmentId);

    @Query("SELECT COUNT(a) > 0 FROM Appointment a WHERE a.originalAppointment.id = :originalAppointmentId")
    boolean hasReexam(@Param("originalAppointmentId") Long originalAppointmentId);

    @Modifying
    @Query(nativeQuery = true, value = "UPDATE appointment SET status = 'CONFIRMED' WHERE original_appointment_id = :originalAppointmentId AND status = 'REEXAM'")
    int updateReexamStatusToConfirmed(@Param("originalAppointmentId") Long originalAppointmentId);

    @Query(nativeQuery = true, value = "SELECT id, original_appointment_id, status FROM appointment WHERE original_appointment_id = :originalAppointmentId")
    List<Object[]> debugFindReexamByOriginalId(@Param("originalAppointmentId") Long originalAppointmentId);
}
