package com.dentalclinic.repository;

import com.dentalclinic.model.schedule.BusySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DentistBusyScheduleRepository extends JpaRepository<BusySchedule, Long> {
    List<BusySchedule> findAllByOrderByCreatedAtDesc();

    // Thêm hàm này để đếm số yêu cầu nghỉ trong tháng (không tính những cái bị REJECTED)
    @Query("SELECT COUNT(b) FROM BusySchedule b WHERE b.dentist.id = :dentistId " +
            "AND b.status <> 'REJECTED' " +
            "AND b.startDate >= :startOfMonth AND b.startDate <= :endOfMonth")
    long countApprovedLeavesInMonth(@Param("dentistId") Long dentistId,
                                    @Param("startOfMonth") LocalDate startOfMonth,
                                    @Param("endOfMonth") LocalDate endOfMonth);
    @Query("SELECT COUNT(b) FROM BusySchedule b WHERE b.dentist.id = :dentistId " +
            "AND b.status <> 'REJECTED' " +
            "AND b.startDate >= :startOfMonth AND b.startDate <= :endOfMonth")
    long countLeavesInMonth(@Param("dentistId") Long dentistId,
                            @Param("startOfMonth") LocalDate startOfMonth,
                            @Param("endOfMonth") LocalDate endOfMonth);

    List<BusySchedule> findByDentistIdOrderByCreatedAtDesc(Long dentistId);

    @Query("SELECT COUNT(b) FROM BusySchedule b WHERE b.dentist.id = :dentistId " +
            "AND b.status != 'REJECTED' " +
            "AND (:startDate <= b.endDate AND :endDate >= b.startDate)")
    long countOverlappingRequests(@Param("dentistId") Long dentistId,
                                  @Param("startDate") LocalDate startDate,
                                  @Param("endDate") LocalDate endDate);
}