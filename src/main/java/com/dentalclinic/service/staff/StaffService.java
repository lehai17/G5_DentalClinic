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

    // 1. Lưu nhï¿½n viên mới
    @Transactional
    public void saveStaff(StaffDTO dto) {
        // KIỂM TRA EMAIL TỒN T� I TRƯỚC
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email n\u00e0y \u0111\u00e3 \u0111\u01b0\u1ee3c s\u1eed d\u1ee5ng trong h\u1ec7 th\u1ed1ng!");
        }
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword("123456");
        user.setRole(Role.STAFF);
        user.setStatus(UserStatus.ACTIVE);
        user.setDateOfBirth(dto.getDateOfBirth());

        // Ã‰p kiểu String từ DTO sang Enum Gender của User Entity
        if (dto.getGender() != null) {
            user.setGender(Gender.valueOf(dto.getGender().toUpperCase()));
        }

        userRepository.save(user);

        StaffProfile profile = new StaffProfile();
        profile.setUser(user);
        profile.setFullName(dto.getFullName());
        profile.setPhone(dto.getPhone());
        profile.setPosition(dto.getPosition());

        staffProfileRepository.save(profile);
    }

    // 2. Khóa t� i khoản nhï¿½n viên
    @Transactional
    public void deactivateStaff(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);
        });
    }

    // 3. Tìm kiếm v?  lọc nhï¿½n viên an to� n
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

        // Trï¿½nh lỗi chuỗi rỗng khi lọc vị trí
        String posParam = (position != null && !position.isEmpty()) ? position : null;

        return staffProfileRepository.filterStaffs(status, posParam);
    }

    // 4. Cï¿½c phương thức bổ trợ cho Stat Cards
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
                .orElseThrow(() -> new RuntimeException("Kh\u00f4ng t\u00ecm th\u1ea5y ng\u01b0\u1eddi d\u00f9ng"));
        user.setStatus(status);
        userRepository.save(user);
    }

    // 5. Lấy thông tin nhï¿½n viên để cập nhật
    public com.dentalclinic.dto.admin.UpdateStaffDTO getStaffForUpdate(Long id) {
        StaffProfile profile = staffProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kh\u00f4ng t\u00ecm th\u1ea5y nh\u00e2n vi\u00ean"));

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

    // 6. Cập nhật nhï¿½n viên
    @Transactional
    public void updateStaff(Long id, com.dentalclinic.dto.admin.UpdateStaffDTO dto) {
        StaffProfile profile = staffProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nhï¿½n viên"));

        User user = profile.getUser();
        if (user == null) {
            throw new RuntimeException("L\u1ed7i d\u1eef li\u1ec7u: Nh\u00e2n vi\u00ean kh\u00f4ng c\u00f3 t\u00e0i kho\u1ea3n user");
        }

        // Kiểm tra trùng email (nếu email bị thay đổi)
        if (!user.getEmail().equals(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email n\u00e0y \u0111\u00e3 \u0111\u01b0\u1ee3c s\u1eed d\u1ee5ng trong h\u1ec7 th\u1ed1ng!");
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
                .orElseThrow(() -> new RuntimeException("Kh\u00f4ng t\u00ecm th\u1ea5y nh\u00e2n vi\u00ean"));

        User user = profile.getUser();
        if (user == null) {
            throw new RuntimeException("L\u1ed7i d\u1eef li\u1ec7u: Nh\u00e2n vi\u00ean kh\u00f4ng c\u00f3 t\u00e0i kho\u1ea3n user");
        }

        try {
            staffProfileRepository.delete(profile);
            userRepository.delete(user);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException(
                    "Kh\u00f4ng th\u1ec3 x\u00f3a do t\u1ed3n t\u1ea1i d\u1eef li\u1ec7u l\u1ecbch s\u1eed li\u00ean k\u1ebft v\u1edbi nh\u00e2n vi\u00ean n\u00e0y. Vui l\u00f2ng d\u00f9ng t\u00ednh n\u0103ng 'Kh\u00f3a'.");
        }
    }
}


