package com.dentalclinic.repository;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.user.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DentistProfileRepository extends JpaRepository<DentistProfile, Long> {

    // 1. Tìm kiếm theo chuyên khoa
    List<DentistProfile> findBySpecialization(String specialization);

    // 2. Tìm kiếm theo số năm kinh nghiệm
    List<DentistProfile> findByExperienceYearsGreaterThanEqual(int years);

    // 3. Kiểm tra hồ sơ tồn tại
    // dùng trong toàn bộ flow hiện tại
    Optional<DentistProfile> findByUser_Id(Long userId);

    boolean existsByUser_Id(Long userId);

    /**
     * CẬP NHẬT QUAN TRỌNG: Sử dụng JOIN FETCH để sửa lỗi khoảng trắng dữ liệu
     * JOIN FETCH d.user: Lấy thông tin tài khoản (Email, Status)
     * LEFT JOIN FETCH d.schedules: Lấy toàn bộ lịch làm việc của bác sĩ
     */
    @Query("SELECT DISTINCT d FROM DentistProfile d " +
            "JOIN FETCH d.user u " +
            "LEFT JOIN FETCH d.schedules " +
            "WHERE (:specialty IS NULL OR d.specialization = :specialty OR :specialty = '') " +
            "AND (:status IS NULL OR u.status = :status)")
    List<DentistProfile> filterDentists(@Param("specialty") String specialty,
                                        @Param("status") UserStatus status);

    // 4. Đếm số lượng bác sĩ theo trạng thái để hiển thị Stat Cards
    @Query("SELECT COUNT(d) FROM DentistProfile d WHERE d.user.status = :status")
    long countByUserStatus(@Param("status") UserStatus status);
    @Query("SELECT p FROM DentistProfile p JOIN p.user u ORDER BY u.createdAt DESC")
    List<DentistProfile> findAllOrderByNewest();
    // Tìm kiếm theo tên hoặc chuyên khoa, sắp xếp người mới nhất lên đầu
//    @Query("SELECT p FROM DentistProfile p JOIN p.user u " +
//            "WHERE p.fullName LIKE %:keyword% OR p.specialization LIKE %:keyword% " +
//            "ORDER BY u.createdAt DESC")
//    List<DentistProfile> findByKeyword(@Param("keyword") String keyword);
}
