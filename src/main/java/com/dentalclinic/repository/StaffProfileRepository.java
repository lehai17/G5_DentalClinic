package com.dentalclinic.repository;

import com.dentalclinic.model.profile.StaffProfile;
import com.dentalclinic.model.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StaffProfileRepository extends JpaRepository<StaffProfile, Long> {

    // 1. TÃ¬m kiáº¿m há»“ sÆ¡ nhï¿½n viÃªn dá»±a trÃªn ID cá»§a User
    Optional<StaffProfile> findByUserId(Long userId);

    // 2. TÃ¬m nhï¿½n viÃªn theo sá»‘ Ä‘iá»‡n thoáº¡i
    Optional<StaffProfile> findByPhone(String phone);

    /**
     * Lá»c danh sï¿½ch nhï¿½n viÃªn theo Tráº¡ng thï¿½i tÃ i khoáº£n vÃ  Vá»‹ trÃ­
     * cÃ´ng viá»‡c
     * Sá»­ dá»¥ng JOIN Ä‘á»ƒ truy cáº­p thuá»™c tÃ­nh status náº±m trong thá»±c
     * thá»ƒ User
     */
    // @Query("SELECT s FROM StaffProfile s JOIN s.user u WHERE " +
    // "(:status IS NULL OR u.status = :status) AND " +
    // "(:position IS NULL OR s.position = :position OR :position = '')")
    // List<StaffProfile> filterStaffs(@Param("status") UserStatus status,
    // @Param("position") String position);
    // @Query("SELECT s FROM StaffProfile s JOIN s.user u WHERE " +
    // "(:status IS NULL OR u.status = :status) AND (" + // Bá»• sung lá»c status
    // "(:position = 'Other' AND s.position != 'Receptionist') OR " +
    // "(:position != 'Other' AND s.position = :position) OR " +
    // "(:position IS NULL OR :position = '')" +
    // ")")
    // List<StaffProfile> filterStaffs(@Param("status") UserStatus status,
    // @Param("position") String position);
    /**
     * Lọc danh sách nhân viên theo Từ khóa, Trạng thái tài khoản và Vị trí công
     * việc.
     * Hỗ trợ tìm kiếm theo Họ tên hoặc Số điện thoại.
     */
    @Query("SELECT s FROM StaffProfile s JOIN s.user u WHERE " +
            "(:keyword IS NULL OR LOWER(s.fullName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR s.phone LIKE CONCAT('%', :keyword, '%')) AND "
            +
            "(:status IS NULL OR u.status = :status) AND (" +
            "(:position = 'Other' AND s.position != 'Receptionist') OR " +
            "(:position != 'Other' AND s.position = :position) OR " +
            "(:position IS NULL OR :position = '')" +
            ")")
    List<StaffProfile> searchStaffs(@Param("keyword") String keyword,
            @Param("status") UserStatus status,
            @Param("position") String position);

    /**
     * Äáº¿m sá»‘ lÆ°á»£ng nhï¿½n viÃªn dá»±a trÃªn tráº¡ng thï¿½i (DÃ¹ng cho cï¿½c
     * Stat Cards)
     */
    @Query("SELECT COUNT(s) FROM StaffProfile s WHERE s.user.status = :status")
    long countByUserStatus(@Param("status") UserStatus status);
}
