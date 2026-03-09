package com.dentalclinic.repository;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface DentistProfileRepository extends JpaRepository<DentistProfile, Long> {

    // 1. TÃ¬m kiáº¿m theo chuyÃªn khoa
    List<DentistProfile> findBySpecialization(String specialization);

    // 2. TÃ¬m kiáº¿m theo sá»‘ nÄƒm kinh nghiá»‡m
    List<DentistProfile> findByExperienceYearsGreaterThanEqual(int years);

    // 3. Kiá»ƒm tra há»“ sÆ¡ tá»“n táº¡i
    // dÃ¹ng trong toÃ n bá»™ flow hiá»‡n táº¡i
    Optional<DentistProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    /**
     * Cáº¬P NHáº¬T QUAN TRá»ŒNG: Sá»­ dá»¥ng JOIN FETCH Ä‘á»ƒ sá»­a lá»—i khoáº£ng tráº¯ng dá»¯ liá»‡u
     * JOIN FETCH d.user: Láº¥y thÃ´ng tin tÃ i khoáº£n (Email, Status)
     * LEFT JOIN FETCH d.schedules: Láº¥y toÃ n bá»™ lá»‹ch lÃ m viá»‡c cá»§a bÃ¡c sÄ©
     */
    @Query("SELECT DISTINCT d FROM DentistProfile d " +
            "JOIN FETCH d.user u " +
            "LEFT JOIN FETCH d.schedules " +
            "WHERE (:specialty IS NULL OR d.specialization = :specialty OR :specialty = '') " +
            "AND (:status IS NULL OR u.status = :status)")
    List<DentistProfile> filterDentists(@Param("specialty") String specialty,
                                        @Param("status") UserStatus status);

    // 4. Äáº¿m sá»‘ lÆ°á»£ng bÃ¡c sÄ© theo tráº¡ng thÃ¡i Ä‘á»ƒ hiá»ƒn thá»‹ Stat Cards
    @Query("SELECT COUNT(d) FROM DentistProfile d WHERE d.user.status = :status")
    long countByUserStatus(@Param("status") UserStatus status);
    @Query("SELECT p FROM DentistProfile p JOIN p.user u ORDER BY u.createdAt DESC")
    List<DentistProfile> findAllOrderByNewest();

    Optional<Object> findByUserEmail(String email);
    // TÃ¬m kiáº¿m theo tÃªn hoáº·c chuyÃªn khoa, sáº¯p xáº¿p ngÆ°á»i má»›i nháº¥t lÃªn Ä‘áº§u
//    @Query("SELECT p FROM DentistProfile p JOIN p.user u " +
//            "WHERE p.fullName LIKE %:keyword% OR p.specialization LIKE %:keyword% " +
//            "ORDER BY u.createdAt DESC")
//    List<DentistProfile> findByKeyword(@Param("keyword") String keyword);

    @Query("""
    SELECT d FROM DentistProfile d
    JOIN d.schedules s 
    WHERE d.user.status = 'ACTIVE' 
    AND s.dayOfWeek = :dayOfWeek
    AND d.id NOT IN (
        SELECT b.dentist.id FROM BusySchedule b 
        WHERE b.status = 'APPROVED' 
        AND :targetDate BETWEEN b.startDate AND b.endDate
    )
""")
    List<DentistProfile> findAvailableDentistsWithSchedule(
            @Param("targetDate") LocalDate targetDate,
            @Param("dayOfWeek") java.time.DayOfWeek dayOfWeek
    );
    @Query("""
    SELECT d FROM DentistProfile d 
    WHERE d.id NOT IN (
        SELECT b.dentist.id FROM BusySchedule b 
        WHERE b.status = 'APPROVED' 
        AND :targetDate BETWEEN b.startDate AND b.endDate
    )
""")
    List<DentistProfile> findAvailableDentistsForDate(@Param("targetDate") LocalDate targetDate);
}
