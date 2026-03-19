package com.dentalclinic.service.dentist;

import com.dentalclinic.dto.admin.DentistDTO;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.model.user.Gender;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.DentistScheduleRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.appointment.SlotCapacitySyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    @Autowired
    private SlotCapacitySyncService slotCapacitySyncService;

    private final String UPLOAD_DIR = "uploads/dentists/";

    private String sanitizeFileName(String fileName) {
        if (fileName == null) {
            return "avatar.png";
        }
        String normalized = java.text.Normalizer.normalize(fileName, java.text.Normalizer.Form.NFD);
        normalized = normalized.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.replaceAll("[^a-zA-Z0-9.-]", "_").replaceAll("__+", "_");
    }

    private String saveImage(MultipartFile image) {
        try {
            String originalFileName = image.getOriginalFilename();
            String sanitizedName = sanitizeFileName(originalFileName);
            String fileName = System.currentTimeMillis() + "_" + sanitizedName;
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
        if (dto.getDateOfBirth() == null) {
            throw new IllegalArgumentException("Ngày sinh không được để trống.");
        }

        LocalDate today = LocalDate.now();
        int age = Period.between(dto.getDateOfBirth(), today).getYears();

        if (age < 25) {
            throw new IllegalArgumentException("Bác sĩ phải từ 25 tuổi trở lên (Hiện tại: " + age + " tuổi).");
        }

        int experienceYears = dto.getExperienceYears();
        int maxAllowedExperience = age - 22;

        if (experienceYears > maxAllowedExperience) {
            throw new IllegalArgumentException("Số năm kinh nghiệm (" + experienceYears
                    + ") không hợp lệ cho bác sĩ " + age + " tuổi. (Tối đa cho phép: "
                    + maxAllowedExperience + " năm).");
        }

        User user = new User();
        user.setEmail(dto.getEmail());
        user.setPassword("123456");
        user.setRole(Role.DENTIST);
        user.setStatus(UserStatus.ACTIVE);
        userRepository.save(user);

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

        user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender() != null) {
            user.setGender(Gender.valueOf(dto.getGender().toUpperCase()));
        }
        userRepository.save(user);

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
                    dentistScheduleRepository.save(schedule);
                } catch (IllegalArgumentException e) {
                    System.err.println("Ngày không hợp lệ: " + dayStr);
                }
            }
        }
        slotCapacitySyncService.syncAllFutureCapacities();
    }

    /**
     * Hỗ trợ parse thời gian an toàn để tránh lỗi dữ liệu đầu vào.
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

    public List<DentistProfile> searchDentists(String keyword, String specialty, String statusStr) {
        UserStatus status = null;
        if (statusStr != null && !statusStr.isEmpty()) {
            try {
                status = UserStatus.valueOf(statusStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                status = null;
            }
        }

        String specialtyParam = (specialty != null && !specialty.isEmpty()) ? specialty : null;
        String keywordParam = (keyword != null && !keyword.trim().isEmpty()) ? keyword.trim() : null;

        return dentistProfileRepository.filterDentists(keywordParam, specialtyParam, status);
    }

    public long countByStatus(String statusStr) {
        try {
            return dentistProfileRepository.countByUserStatus(UserStatus.valueOf(statusStr.toUpperCase()));
        } catch (Exception e) {
            return 0;
        }
    }

    public void deactivateDentist(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setStatus(UserStatus.LOCKED);
            userRepository.save(user);
            slotCapacitySyncService.syncAllFutureCapacities();
            System.out.println("Đã khóa tài khoản bác sĩ có User ID: " + userId);
        });
    }

    public void updateDentistStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(status);
        userRepository.save(user);
        slotCapacitySyncService.syncAllFutureCapacities();
    }

    public DentistDTO getDentistById(Long id) {
        DentistProfile profile = dentistProfileRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy hồ sơ bác sĩ!"));

        return convertToDTO(profile);
    }

    private DentistDTO convertToDTO(DentistProfile profile) {
        DentistDTO dto = new DentistDTO();

        dto.setId(profile.getId());
        dto.setFullName(profile.getFullName() != null ? profile.getFullName() : "N/A");
        dto.setPhone(profile.getPhone() != null ? profile.getPhone() : "N/A");
        dto.setSpecialization(profile.getSpecialization() != null ? profile.getSpecialization() : "N/A");
        dto.setExperienceYears(profile.getExperienceYears());
        dto.setBio(profile.getBio() != null ? profile.getBio() : "Chưa có tiểu sử.");
        dto.setAvatarPath(profile.getAvatar());

        if (profile.getUser() != null) {
            dto.setEmail(profile.getUser().getEmail());
            dto.setDateOfBirth(profile.getUser().getDateOfBirth());
            if (profile.getUser().getGender() != null) {
                dto.setGender(profile.getUser().getGender().name());
            }
        }

        if (profile.getSchedules() != null) {
            dto.setSchedules(profile.getSchedules());
        } else {
            dto.setSchedules(new ArrayList<>());
        }

        System.out.println("DEBUG: Đã lấy xong dữ liệu cho email: " + dto.getEmail());
        return dto;
    }

    public List<DentistDTO> getAllDentists() {
        List<DentistProfile> profiles = dentistProfileRepository.findAllOrderByNewest();
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

        if (!user.getEmail().equals(dto.getEmail()) && userRepository.existsByEmail(dto.getEmail())) {
            throw new RuntimeException("Email này đã được sử dụng trong hệ thống!");
        }

        if (dto.getDateOfBirth() != null) {
            LocalDate today = LocalDate.now();
            int age = Period.between(dto.getDateOfBirth(), today).getYears();
            if (age < 25) {
                throw new IllegalArgumentException("Bác sĩ phải từ 25 tuổi trở lên (Hiện tại: " + age + " tuổi).");
            }
            int maxAllowedExperience = age - 22;
            if (dto.getExperienceYears() > maxAllowedExperience) {
                throw new IllegalArgumentException("Số năm kinh nghiệm (" + dto.getExperienceYears()
                        + ") không hợp lệ cho bác sĩ " + age + " tuổi. (Tối đa: "
                        + maxAllowedExperience + " năm).");
            }
        }

        user.setEmail(dto.getEmail());
        user.setDateOfBirth(dto.getDateOfBirth());
        if (dto.getGender() != null) {
            user.setGender(Gender.valueOf(dto.getGender().toUpperCase()));
        }
        if (dto.getStatus() != null) {
            user.setStatus(UserStatus.valueOf(dto.getStatus().toUpperCase()));
        }
        userRepository.save(user);

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
        slotCapacitySyncService.syncAllFutureCapacities();
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
            slotCapacitySyncService.syncAllFutureCapacities();
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            throw new RuntimeException(
                    "Không thể xóa do nha sĩ đã có lịch sử khám bệnh. Vui lòng sử dụng tính năng 'Khóa' thay vì xóa.");
        }
    }

    public List<DentistProfile> getAvailableDentistsForDate(LocalDate date) {
        DayOfWeek dayOfWeek = date.getDayOfWeek();
        return dentistProfileRepository.findAvailableDentistsWithSchedule(date, dayOfWeek);
    }
}
