package com.dentalclinic.service.dentist;

import com.dentalclinic.dto.admin.DentistDTO;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.model.user.Gender;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.DentistScheduleRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.model.appointment.AppointmentStatus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class DentistService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DentistProfileRepository dentistProfileRepository;
    @Autowired
    private DentistScheduleRepository dentistScheduleRepository;
    @Autowired
    private AppointmentRepository appointmentRepository;

    private final String UPLOAD_DIR = "src/main/resources/static/uploads/dentists/";

    private String saveImage(MultipartFile image) {
        try {
            String originalFileName = image.getOriginalFilename();
            if (originalFileName == null)
                originalFileName = "avatar.png";
            String fileName = StringUtils.cleanPath(originalFileName);
            fileName = System.currentTimeMillis() + "_" + fileName;
            Path uploadPath = Paths.get(UPLOAD_DIR);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            try (InputStream inputStream = image.getInputStream()) {
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
                return "/uploads/dentists/" + fileName;
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not save image file: " + e.getMessage());
        }
    }

    @Transactional
    public void saveDentist(DentistDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email này đã được sử dụng trong hệ thống!");
        }
        // 1. Kiểm tra ngày sinh và tính tuổi
        if (dto.getDateOfBirth() == null) {
            throw new IllegalArgumentException("Ngày sinh không được để trống.");
        }

        LocalDate today = LocalDate.now();
        int age = Period.between(dto.getDateOfBirth(), today).getYears();

        // ĐIỀU KIỆN 1: Bác sĩ phải từ 25 tuổi trở lên
        if (age < 25) {
            throw new IllegalArgumentException("Bác sĩ phải từ 25 tuổi trở lên (Hiện tại: " + age + " tuổi).");
        }

        // ĐIỀU KIỆN 2: Kiểm tra năm kinh nghiệm (không quá tuổi trừ đi 22 năm học đại
        // học)
        int experienceYears = dto.getExperienceYears();
        int maxAllowedExperience = age - 22;

        if (experienceYears > maxAllowedExperience) {
            throw new IllegalArgumentException("Số năm kinh nghiệm (" + experienceYears +
                    ") không hợp lệ cho bác sĩ " + age + " tuổi. (Tối đa cho phép: " + maxAllowedExperience + " năm).");
        }
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
        profile.setSpecialization(dto.getSpecialization());
        profile.setExperienceYears(dto.getExperienceYears());
        profile.setPhone(dto.getPhone());

        if (dto.getAvatarFile() != null && !dto.getAvatarFile().isEmpty()) {
            String avatarUrl = saveImage(dto.getAvatarFile());
            profile.setAvatar(avatarUrl);
        }

        dentistProfileRepository.save(profile);
        // ĐÂY LÀ DÒNG QUAN TRỌNG NHẤT: Lưu dữ liệu vào bảng users
        user.setDateOfBirth(dto.getDateOfBirth());

        userRepository.save(user);
        if (dto.getGender() != null) {
            user.setGender(Gender.valueOf(dto.getGender().toUpperCase()));
        }
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
    // public List<DentistDTO> searchByKeyword(String keyword) {
    // List<DentistProfile> profiles;
    //
    // if (keyword != null && !keyword.trim().isEmpty()) {
    // // Gọi hàm tìm kiếm từ Repository đã làm ở bước trước
    // profiles = dentistProfileRepository.findByKeyword(keyword);
    // } else {
    // // Trả về danh sách mặc định sắp xếp mới nhất lên đầu
    // profiles = dentistProfileRepository.findAllOrderByNewest();
    // }
    //
    // // Đảm bảo hàm convertToDTO đã tồn tại và hoạt động đúng
    // return profiles.stream()
    // .map(this::convertToDTO)
    // .collect(Collectors.toList());
    // }

    // Hàm đếm số lượng cho Stat Cards
    public long countByStatus(String statusStr) {
        try {
            return dentistProfileRepository.countByUserStatus(UserStatus.valueOf(statusStr.toUpperCase()));
        } catch (Exception e) {
            return 0;
        }
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

    public DentistDTO getDentistById(Long id) {
        DentistProfile profile = dentistProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bác sĩ!"));

        return convertToDTO(profile);
    }

    private DentistDTO convertToDTO(DentistProfile profile) {
        DentistDTO dto = new DentistDTO();

        // 1. Dữ liệu từ DentistProfile
        dto.setId(profile.getId());
        dto.setFullName(profile.getFullName() != null ? profile.getFullName() : "N/A");
        dto.setPhone(profile.getPhone() != null ? profile.getPhone() : "N/A");
        dto.setSpecialization(profile.getSpecialization() != null ? profile.getSpecialization() : "N/A");
        dto.setExperienceYears(profile.getExperienceYears());
        dto.setBio(profile.getBio() != null ? profile.getBio() : "Chưa có tiểu sử.");
        dto.setAvatarPath(profile.getAvatar());
        // 2. Dữ liệu từ User (Phải dùng đúng tên hàm trong User.java)
        if (profile.getUser() != null) {
            dto.setEmail(profile.getUser().getEmail());
            // Đảm bảo tên hàm này khớp với User.java (getDateOfBirth hoặc getDob)
            dto.setDateOfBirth(profile.getUser().getDateOfBirth());
            if (profile.getUser().getGender() != null) {
                dto.setGender(profile.getUser().getGender().name());
            }
        }

        // 3. Xử lý Lịch trực an toàn
        if (profile.getSchedules() != null) {
            dto.setSchedules(profile.getSchedules());
        } else {
            dto.setSchedules(new ArrayList<>());
        }

        System.out.println("DEBUG: Đã lấy xong dữ liệu cho email: " + dto.getEmail()); // Sẽ hiện nếu code chạy hết
        return dto;
    }

    public List<DentistDTO> getAllDentists() {
        // Gọi hàm truy vấn tùy chỉnh để đưa bác sĩ Tuấn lên đầu
        List<DentistProfile> profiles = dentistProfileRepository.findAllOrderByNewest();

        // Chuyển đổi List Entity sang List DTO
        return profiles.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    public com.dentalclinic.dto.admin.UpdateDentistDTO getDentistForUpdate(Long id) {
        DentistProfile profile = dentistProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ"));

        com.dentalclinic.dto.admin.UpdateDentistDTO dto = new com.dentalclinic.dto.admin.UpdateDentistDTO();
        dto.setId(profile.getId());
        dto.setFullName(profile.getFullName());
        dto.setPhone(profile.getPhone());
        dto.setSpecialization(profile.getSpecialization());
        dto.setExperienceYears(profile.getExperienceYears());
        dto.setBio(profile.getBio());
        dto.setAvatarPath(profile.getAvatar());

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

        if (profile.getSchedules() != null && !profile.getSchedules().isEmpty()) {
            List<String> days = new ArrayList<>();
            for (DentistSchedule s : profile.getSchedules()) {
                days.add(s.getDayOfWeek().name());
                dto.setShiftStartTime(s.getStartTime() != null ? s.getStartTime().toString() : null);
                dto.setShiftEndTime(s.getEndTime() != null ? s.getEndTime().toString() : null);
            }
            dto.setAvailableDays(days);
        }

        return dto;
    }

    @Transactional
    public void updateDentist(Long id, com.dentalclinic.dto.admin.UpdateDentistDTO dto) {
        DentistProfile profile = dentistProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ"));

        User user = profile.getUser();
        if (user == null) {
            throw new RuntimeException("Lỗi dữ liệu: Bác sĩ không có tài khoản user");
        }

        // Kiểm tra trùng email
        if (!user.getEmail().equals(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email này đã được sử dụng trong hệ thống!");
        }

        // Validate độ tuổi (>= 25)
        if (dto.getDateOfBirth() != null) {
            LocalDate today = LocalDate.now();
            int age = Period.between(dto.getDateOfBirth(), today).getYears();
            if (age < 25) {
                throw new IllegalArgumentException("Bác sĩ phải từ 25 tuổi trở lên (Hiện tại: " + age + " tuổi).");
            }
            int maxAllowedExperience = age - 22;
            if (dto.getExperienceYears() > maxAllowedExperience) {
                throw new IllegalArgumentException("Số năm kinh nghiệm (" + dto.getExperienceYears() +
                        ") không hợp lệ cho bác sĩ " + age + " tuổi. (Tối đa: " + maxAllowedExperience + " năm).");
            }
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
        profile.setSpecialization(dto.getSpecialization());
        profile.setExperienceYears(dto.getExperienceYears());
        profile.setBio(dto.getBio());

        if (dto.getAvatarFile() != null && !dto.getAvatarFile().isEmpty()) {
            String avatarUrl = saveImage(dto.getAvatarFile());
            profile.setAvatar(avatarUrl);
        }

        dentistProfileRepository.save(profile);

        // Cập nhật Lịch làm việc
        dentistScheduleRepository.deleteAll(profile.getSchedules());
        profile.getSchedules().clear();

        if (dto.getAvailableDays() != null && !dto.getAvailableDays().isEmpty()) {
            LocalTime startTime = parseTimeSafe(dto.getShiftStartTime());
            LocalTime endTime = parseTimeSafe(dto.getShiftEndTime());

            for (String dayStr : dto.getAvailableDays()) {
                try {
                    DentistSchedule schedule = new DentistSchedule();
                    schedule.setDentist(profile);
                    schedule.setDayOfWeek(DayOfWeek.valueOf(dayStr.toUpperCase()));
                    schedule.setStartTime(startTime);
                    schedule.setEndTime(endTime);
                    profile.getSchedules().add(schedule);
                    dentistScheduleRepository.save(schedule);
                } catch (IllegalArgumentException e) {
                    System.err.println("Ngày không hợp lệ: " + dayStr);
                }
            }
        }
    }

    @Transactional
    public void deleteDentist(Long id) {
        DentistProfile profile = dentistProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy nha sĩ!"));

        List<AppointmentStatus> excludedStatuses = List.of(AppointmentStatus.CANCELLED, AppointmentStatus.COMPLETED);
        int upcomingCount = appointmentRepository.countUpcomingAppointments(id, LocalDate.now(), excludedStatuses);

        if (upcomingCount > 0) {
            throw new RuntimeException("Không thể xóa do nha sĩ có lịch hẹn sắp tới!");
        }

        User user = profile.getUser();
        try {
            dentistProfileRepository.delete(profile);
            if (user != null) {
                userRepository.delete(user);
            }
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException(
                    "Không thể xóa do nha sĩ đã có lịch sử khám bệnh. Vui lòng sử dụng tính năng 'Khóa' thay vì xóa.");
        }
    }
}
