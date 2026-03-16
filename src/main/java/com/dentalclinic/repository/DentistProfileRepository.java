package com.dentalclinic.repository;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.model.schedule.BusySchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;

@Repository
public interface DentistProfileRepository extends JpaRepository<DentistProfile, Long> {

    List<DentistProfile> findBySpecialization(String specialization);

    List<DentistProfile> findByExperienceYearsGreaterThanEqual(int years);

    Optional<DentistProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    @Query("SELECT DISTINCT d FROM DentistProfile d " +
            "JOIN FETCH d.user u " +
            "LEFT JOIN FETCH d.schedules " +
            "WHERE (:specialty IS NULL OR d.specialization = :specialty OR :specialty = '') " +
            "AND (:status IS NULL OR u.status = :status) " +
            "AND (:keyword IS NULL OR LOWER(d.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(d.specialization) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    List<DentistProfile> filterDentists(@Param("keyword") String keyword,
            @Param("specialty") String specialty,
            @Param("status") UserStatus status);

    @Query("SELECT COUNT(d) FROM DentistProfile d WHERE d.user.status = :status")
    long countByUserStatus(@Param("status") UserStatus status);

    @Query("SELECT p FROM DentistProfile p JOIN p.user u ORDER BY u.createdAt DESC")
    List<DentistProfile> findAllOrderByNewest();

    Optional<Object> findByUserEmail(String email);

    @Query("""
                SELECT DISTINCT d FROM DentistProfile d
                JOIN d.schedules s
                WHERE d.user.status = com.dentalclinic.model.user.UserStatus.ACTIVE
                AND s.dayOfWeek = :dayOfWeek
                AND s.available = true
                AND NOT EXISTS (
                    SELECT 1 FROM BusySchedule b
                    WHERE b.dentist = d
                    AND UPPER(COALESCE(b.status, '')) = 'APPROVED'
                    AND :targetDate BETWEEN b.startDate AND b.endDate
                )
            """)
    List<DentistProfile> findAvailableDentistsWithSchedule(
            @Param("targetDate") LocalDate targetDate,
            @Param("dayOfWeek") DayOfWeek dayOfWeek);

    @Query("""
                SELECT DISTINCT d FROM DentistProfile d
                WHERE d.user.status = com.dentalclinic.model.user.UserStatus.ACTIVE
                AND NOT EXISTS (
                    SELECT 1 FROM BusySchedule b
                    WHERE b.dentist = d
                    AND UPPER(COALESCE(b.status, '')) = 'APPROVED'
                    AND :targetDate BETWEEN b.startDate AND b.endDate
                )
            """)
    List<DentistProfile> findAvailableDentistsForDate(@Param("targetDate") LocalDate targetDate);
}
