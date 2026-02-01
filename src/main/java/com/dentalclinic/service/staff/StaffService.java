package com.dentalclinic.service.staff;

import com.dentalclinic.dto.StaffDTO;
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

    @Autowired private UserRepository userRepository;
    @Autowired private StaffProfileRepository staffProfileRepository;

    // 1. Lưu nhân viên mới
    @Transactional
    public void saveStaff(StaffDTO dto) {
        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword("123456");
        user.setRole(Role.STAFF);
        user.setStatus(UserStatus.ACTIVE);
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
}