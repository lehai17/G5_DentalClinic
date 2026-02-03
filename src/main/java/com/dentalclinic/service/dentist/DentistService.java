package com.dentalclinic.service.dentist;

import com.dentalclinic.dto.DentistDTO;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.DentistScheduleRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

@Service
public class DentistService {

    @Autowired private UserRepository userRepository;
    @Autowired private DentistProfileRepository dentistProfileRepository;
    @Autowired private DentistScheduleRepository dentistScheduleRepository;

    @Transactional
    public void saveDentist(DentistDTO dto) {
        // 1. Tạo tài khoản User đăng nhập
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword("123456"); // Mật khẩu mặc định
        user.setRole(Role.DENTIST);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

        // 2. Tạo hồ sơ bác sĩ (DentistProfile)
        DentistProfile profile = new DentistProfile();
        profile.setUser(user);
        profile.setFullName(dto.getFullName());
        profile.setSpecialization(dto.getSpecialty()); // Lấy từ dropdown Specialty
        profile.setExperienceYears(dto.getExperience());
        profile.setPhone(dto.getPhone());
        dentistProfileRepository.save(profile);

        // 3. Xử lý lịch làm việc dựa trên Enum DayOfWeek
        if (dto.getAvailableDays() != null && !dto.getAvailableDays().isEmpty()) {
            LocalTime startTime = parseTimeSafe(dto.getShiftStartTime());
            LocalTime endTime = parseTimeSafe(dto.getShiftEndTime());

            for (String dayStr : dto.getAvailableDays()) {
                try {
                    DentistSchedule schedule = new DentistSchedule();
                    schedule.setDentist(profile);

                    // Chuyển String ngày (MONDAY, TUESDAY...) sang Enum an toàn
                    schedule.setDayOfWeek(DayOfWeek.valueOf(dayStr.toUpperCase()));

                    schedule.setStartTime(startTime);
                    schedule.setEndTime(endTime);

                    dentistScheduleRepository.save(schedule);
                } catch (IllegalArgumentException e) {
                    // Bỏ qua nếu chuỗi ngày không hợp lệ
                    System.err.println("Ngày không hợp lệ: " + dayStr);
                }
            }
        }
    }

    /**
     * Hàm hỗ trợ parse thời gian an toàn tránh lỗi hệ thống
     */
    private LocalTime parseTimeSafe(String timeStr) {
        if (timeStr != null && !timeStr.trim().isEmpty()) {
            try {
                return LocalTime.parse(timeStr);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
    public List<DentistProfile> searchDentists(String specialty, String statusStr) {
        UserStatus status = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                status = UserStatus.valueOf(statusStr.toUpperCase()); // Chuyển String sang Enum
            } catch (IllegalArgumentException e) {
                status = null;
            }
        }

        // Nếu specialty là chuỗi rỗng, gán null để Repository bỏ qua điều kiện lọc
        String specialtyParam = (specialty != null && !specialty.isEmpty()) ? specialty : null;

        return dentistProfileRepository.filterDentists(specialtyParam, status);
    }

    // Hàm đếm số lượng cho Stat Cards
    public long countByStatus(String statusStr) {
        try {
            return dentistProfileRepository.countByUserStatus(UserStatus.valueOf(statusStr.toUpperCase()));
        } catch (Exception e) { return 0; }
    }
    public void deactivateDentist(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            // Cập nhật trạng thái người dùng thành LOCKED
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);

            // Log thông báo hoặc xử lý logic phụ nếu cần (ví dụ: hủy lịch hẹn tương lai)
            System.out.println("Đã khóa tài khoản bác sĩ có User ID: " + userId);
        });
    }

    public void updateDentistStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(status);
        userRepository.save(user);
    }
}