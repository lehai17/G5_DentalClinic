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

    // =========================================================
    // 1. NHÓM KIỂM TRA TRÙNG LỊCH (OVERLAP LOGIC) - NATIVE QUERY
    // =========================================================

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
          AND id <> :excludeId
          AND status IN (:activeStatuses)
          AND appointment_date = :date
          AND start_time < CAST(:endTime AS TIME)
          AND end_time > CAST(:startTime AS TIME)
        """, nativeQuery = true)
    int checkCustomerOverlapExcludingSelf(
            @Param("userId") Long userId,
            @Param("excludeId") Long excludeId,
            @Param("date") LocalDate date,
            @Param("startTime") LocalTime startTime,
            @Param("endTime") LocalTime endTime,
            @Param("activeStatuses") List<String> activeStatuses);

    // Wrapper methods gọi từ Service
    default boolean hasOverlappingAppointment(Long dId, LocalDate d, LocalTime s, LocalTime e) {
        return checkOverlappingAppointment(dId, d, s, e) > 0;
    }

    default boolean hasOverlappingAppointmentExcludingSelf(Long dId, LocalDate d, LocalTime s, LocalTime e, Long apptId) {
        return checkOverlappingAppointmentExcludingSelf(dId, d, s, e, apptId) > 0;
    }

    default boolean existsCustomerOverlap(Long uId, LocalDate d, LocalTime s, LocalTime e, List<String> states) {
        return checkCustomerOverlap(uId, d, s, e, states) > 0;
    }

    default boolean existsCustomerOverlapExcludingAppointment(Long uId, Long exId, LocalDate d, LocalTime s, LocalTime e, List<String> states) {
        return checkCustomerOverlapExcludingSelf(uId, exId, d, s, e, states) > 0;
    }

    // =========================================================
    // 2. NHÓM TRA CỨU CHO KHÁCH HÀNG (CUSTOMER)
    // =========================================================

    @Query("""
        SELECT a FROM Appointment a 
        WHERE a.customer.id = :customerId 
          AND a.status IN (
            com.dentalclinic.model.appointment.AppointmentStatus.PENDING, 
            com.dentalclinic.model.appointment.AppointmentStatus.CONFIRMED
          ) 
        ORDER BY a.date ASC, a.startTime ASC
    """)
    List<Appointment> findUpcomingAppointmentsByCustomerId(@Param("customerId") Long customerId);

    @Query("""
        SELECT a FROM Appointment a 
        WHERE a.customer.id = :customerId 
          AND a.status = com.dentalclinic.model.appointment.AppointmentStatus.COMPLETED 
        ORDER BY a.date DESC, a.startTime DESC
    """)
    List<Appointment> findCompletedAppointmentsByCustomerId(@Param("customerId") Long customerId);

    Optional<Appointment> findByIdAndCustomer_User_Id(Long appointmentId, Long customerUserId);

    List<Appointment> findByCustomer_User_IdOrderByDateDesc(Long customerUserId);

    Page<Appointment> findByCustomer_User_Id(Long userId, Pageable pageable);

    @Query("""
        SELECT a FROM Appointment a
        LEFT JOIN FETCH a.appointmentSlots ass
        LEFT JOIN FETCH ass.slot
        WHERE a.id = :appointmentId AND a.customer.user.id = :userId
    """)
    Optional<Appointment> findByIdWithSlotsAndCustomerUserId(@Param("appointmentId") Long appointmentId, @Param("userId") Long userId);

    // =========================================================
    // 3. NHÓM TRA CỨU CHO BÁC SĨ (DENTIST & DASHBOARD)
    // =========================================================

    @Query("""
        SELECT a FROM Appointment a
        JOIN FETCH a.customer c
        JOIN FETCH c.user cu
        JOIN FETCH a.service s
        LEFT JOIN FETCH a.dentist d
        WHERE d.id = :dentistProfileId
          AND a.date BETWEEN :start AND :end
          AND a.status NOT IN (com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED)
    """)
    List<Appointment> findScheduleForWeek(
            @Param("dentistProfileId") Long dentistProfileId,
            @Param("start") LocalDate start,
            @Param("end") LocalDate end);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.dentist.id = :dentistId AND a.date >= :currentDate AND a.status NOT IN :excludedStatuses")
    int countUpcomingAppointments(@Param("dentistId") Long dentistId, @Param("currentDate") LocalDate currentDate,
                                  @Param("excludedStatuses") List<AppointmentStatus> excludedStatuses);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.dentist.id = :dentistId AND a.date = :date AND a.status <> com.dentalclinic.model.appointment.AppointmentStatus.CANCELLED")
    long countTotalByDentistAndDate(@Param("dentistId") Long dentistId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.dentist.id = :dentistId AND a.date = :date AND a.status IN (com.dentalclinic.model.appointment.AppointmentStatus.DONE, com.dentalclinic.model.appointment.AppointmentStatus.COMPLETED)")
    long countCompletedByDentistAndDate(@Param("dentistId") Long dentistId, @Param("date") LocalDate date);

    // =========================================================
    // 4. NHÓM FETCH CHI TIẾT
    // =========================================================

    @Query("SELECT a FROM Appointment a JOIN FETCH a.customer JOIN FETCH a.service WHERE a.id = :id")
    Optional<Appointment> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT a FROM Appointment a LEFT JOIN FETCH a.appointmentSlots ass LEFT JOIN FETCH ass.slot WHERE a.id = :id")
    Optional<Appointment> findByIdWithSlots(@Param("id") Long id);

    // =========================================================
    // 5. TÌM KIẾM VÀ PHÂN TRANG
    // =========================================================

    Page<Appointment> findByCustomer_FullNameContainingIgnoreCase(String keyword, Pageable pageable);

    Page<Appointment> findByService_NameContainingIgnoreCase(String serviceKeyword, Pageable pageable);

    Page<Appointment> findByCustomer_FullNameContainingIgnoreCaseAndService_NameContainingIgnoreCase(
            String customerKeyword, String serviceKeyword, Pageable pageable);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByDate(LocalDate date);

    // =========================================================
    // 6. MODIFING & OTHERS
    // =========================================================

    @Modifying
    @Query("DELETE FROM AppointmentSlot aslot WHERE aslot.appointment.id = :appointmentId")
    void deleteAppointmentSlotsByAppointmentId(@Param("appointmentId") Long appointmentId);

    boolean existsBySlot_IdAndStatusNot(Long slotId, AppointmentStatus status);

    default boolean existsByDentist_IdAndDateAndStartTimeLessThanEqualAndEndTimeGreaterThanEqual(
            Long dId, LocalDate d, LocalTime s, LocalTime e) {
        return checkOverlappingAppointment(dId, d, s, e) > 0;
    }

    List<Appointment> findByDateAndStatusIn(LocalDate targetDate, List<AppointmentStatus> pending);

    List<Appointment> findByCustomerId(Long customerId);
}