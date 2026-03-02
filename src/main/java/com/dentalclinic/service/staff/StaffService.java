package com.dentalclinic.service.staff;

import com.dentalclinic.dto.admin.StaffDTO;
import com.dentalclinic.model.user.Gender;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.model.profile.StaffProfile;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.repository.StaffProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StaffProfileRepository staffProfileRepository;

    // 1. Lưu nhân viên mới
    @Transactional
    public void saveStaff(StaffDTO dto) {
        // KIỂM TRA EMAIL TỒN TẠI TRƯỚC
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email này đã được sử dụng trong hệ thống!");
        }
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword("123456");
        user.setRole(Role.STAFF);
        user.setStatus(UserStatus.ACTIVE);
        user.setDateOfBirth(dto.getDateOfBirth());

        // Ép kiểu String từ DTO sang Enum Gender của User Entity
        if (dto.getGender() != null) {
            user.setGender(Gender.valueOf(dto.getGender().toUpperCase()));
        }

        userRepository.save(user);
        userRepository.save(user);

        StaffProfile profile = new StaffProfile();
        profile.setUser(user);
        profile.setFullName(dto.getFullName());
        profile.setPhone(dto.getPhone());
        profile.setPosition(dto.getPosition());

        staffProfileRepository.save(profile);
    }

    // 2. Khóa tài khoản nhân viên
    @Transactional
    public void deactivateStaff(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);
        });
    }

    // 3. Tìm kiếm và lọc nhân viên an toàn
    public List<StaffProfile> searchStaffs(String statusStr, String position) {
        UserStatus status = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                // Chuyển đổi String sang Enum UserStatus
                status = UserStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                status = null; // Trả về null nếu chuỗi không khớp Enum
            }
        }

        // Tránh lỗi chuỗi rỗng khi lọc vị trí
        String posParam = (position != null && !position.isEmpty()) ? position : null;

        return staffProfileRepository.filterStaffs(status, posParam);
    }

    // 4. Các phương thức bổ trợ cho Stat Cards
    public long countTotal() {
        return staffProfileRepository.count();
    }

    public long countByStatus(String statusStr) {
        try {
            UserStatus status = UserStatus.valueOf(statusStr.toUpperCase());
            return staffProfileRepository.countByUserStatus(status);
        } catch (Exception e) {
            return 0;
        }
    }

    public void updateStaffStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(status);
        userRepository.save(user);
    }

    // 5. Lấy thông tin nhân viên để cập nhật
    public com.dentalclinic.dto.admin.UpdateStaffDTO getStaffForUpdate(Long id) {
        StaffProfile profile = staffProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));

        com.dentalclinic.dto.admin.UpdateStaffDTO dto = new com.dentalclinic.dto.admin.UpdateStaffDTO();
        dto.setId(profile.getId());
        dto.setFullName(profile.getFullName());
        dto.setPhone(profile.getPhone());
        dto.setPosition(profile.getPosition());

        if (profile.getUser() != null) {
            dto.setEmail(profile.getUser().getEmail());
            dto.setDateOfBirth(profile.getUser().getDateOfBirth());
            if (profile.getUser().getGender() != null) {
                dto.setGender(profile.getUser().getGender().name());
            }
            if (profile.getUser().getStatus() != null) {
                dto.setStatus(profile.getUser().getStatus().name());
            }
        }
        return dto;
    }

    // 6. Cập nhật nhân viên
    @Transactional
    public void updateStaff(Long id, com.dentalclinic.dto.admin.UpdateStaffDTO dto) {
        StaffProfile profile = staffProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));

        User user = profile.getUser();
        if (user == null) {
            throw new RuntimeException("Lỗi dữ liệu: Nhân viên không có tài khoản user");
        }

        // Kiểm tra trùng email (nếu email bị thay đổi)
        if (!user.getEmail().equals(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email này đã được sử dụng trong hệ thống!");
        }

        // Cập nhật User
        user.setEmail(dto.getEmail());
        user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender() != null) {
            user.setGender(Gender.valueOf(dto.getGender().toUpperCase()));
        }
        if (dto.getStatus() != null) {
            user.setStatus(UserStatus.valueOf(dto.getStatus().toUpperCase()));
        }
        userRepository.save(user);

        // Cập nhật Profile
        profile.setFullName(dto.getFullName());
        profile.setPhone(dto.getPhone());
        profile.setPosition(dto.getPosition());
        staffProfileRepository.save(profile);
    }

    @Transactional
    public void deleteStaff(Long id) {
        StaffProfile profile = staffProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhân viên"));

        User user = profile.getUser();
        if (user == null) {
            throw new RuntimeException("Lỗi dữ liệu: Nhân viên không có tài khoản user");
        }

        try {
            staffProfileRepository.delete(profile);
            userRepository.delete(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException(
                    "Không thể xóa do tồn tại dữ liệu lịch sử liên kết với nhân viên này. Vui lòng dùng tính năng 'Khóa'.");
        }
    }
}
