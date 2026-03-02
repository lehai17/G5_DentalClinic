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

    // 1. Tìm kiếm hồ sơ nhân viên dựa trên ID của User
    Optional<StaffProfile> findByUserId(Long userId);

    // 2. Tìm nhân viên theo số điện thoại
    Optional<StaffProfile> findByPhone(String phone);

    /**
     * Lọc danh sách nhân viên theo Trạng thái tài khoản và Vị trí công việc
     * Sử dụng JOIN để truy cập thuộc tính status nằm trong thực thể User
     */
//    @Query("SELECT s FROM StaffProfile s JOIN s.user u WHERE " +
//            "(:status IS NULL OR u.status = :status) AND " +
//            "(:position IS NULL OR s.position = :position OR :position = '')")
//    List<StaffProfile> filterStaffs(@Param("status") UserStatus status,
//                                    @Param("position") String position);
    @Query("SELECT s FROM StaffProfile s JOIN s.user u WHERE " +
            "(:status IS NULL OR u.status = :status) AND (" + // Bổ sung lọc status
            "(:position = 'Other' AND s.position != 'Receptionist') OR " +
            "(:position != 'Other' AND s.position = :position) OR " +
            "(:position IS NULL OR :position = '')" +
            ")")
    List<StaffProfile> filterStaffs(@Param("status") UserStatus status,
                                    @Param("position") String position);

    /**
     * Đếm số lượng nhân viên dựa trên trạng thái (Dùng cho các Stat Cards)
     */
    @Query("SELECT COUNT(s) FROM StaffProfile s WHERE s.user.status = :status")
    long countByUserStatus(@Param("status") UserStatus status);
}
