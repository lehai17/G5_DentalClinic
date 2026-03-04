package com.dentalclinic.service.admin;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.BusySchedule;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.SlotRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdminBusyScheduleService {

    private final DentistBusyScheduleRepository repository;
    private final SlotRepository slotRepository;
    private final DentistProfileRepository dentistProfileRepository;

    public AdminBusyScheduleService(DentistBusyScheduleRepository repository,
                                    SlotRepository slotRepository,
                                    DentistProfileRepository dentistProfileRepository) {
        this.repository = repository;
        this.slotRepository = slotRepository;
        this.dentistProfileRepository = dentistProfileRepository;
    }

    public List<BusySchedule> getAllRequests() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void updateStatus(Long requestId, String status) {
        BusySchedule request = repository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn yêu cầu với ID: " + requestId));

        request.setStatus(status);
        repository.save(request);

        // Nếu Admin duyệt, tiến hành khóa các Slot lịch hẹn của bác sĩ đó
        if ("APPROVED".equalsIgnoreCase(status)) {
            lockDentistSlots(request);
        }
    }

    private void lockDentistSlots(BusySchedule request) {
        var startDateTime = request.getStartDate().atTime(8, 0);
        var endDateTime = request.getEndDate().atTime(17, 0);

        // Cần đảm bảo SlotRepository có method disable các slot của Dentist cụ thể trong khoảng thời gian này
        slotRepository.disableSlotsInPeriod(startDateTime, endDateTime);
    }

    @Transactional
    public void submitBusyRequest(Long dentistId, LocalDate start, LocalDate end, String reason) {
        // Logic kiểm tra hạn mức nghỉ (tối đa 2 buổi/tháng)
        LocalDate firstDayOfMonth = start.withDayOfMonth(1);
        LocalDate lastDayOfMonth = start.withDayOfMonth(start.lengthOfMonth());

        long count = repository.countLeavesInMonth(dentistId, firstDayOfMonth, lastDayOfMonth);

        if (count >= 2) {
            throw new RuntimeException("Bác sĩ đã dùng hết giới hạn nghỉ trong tháng " + start.getMonthValue() + " (Tối đa 2 lần/tháng)!");
        }

        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Bác sĩ không tồn tại"));

        BusySchedule busy = new BusySchedule();
        busy.setDentist(dentist);
        busy.setStartDate(start);
        busy.setEndDate(end);
        busy.setReason(reason);
        busy.setStatus("PENDING");

        repository.save(busy);
    }

    public List<BusySchedule> getRequestsByDentist(Long dentistId) {
        return repository.findByDentistIdOrderByCreatedAtDesc(dentistId);
    }

    public List<BusySchedule> searchAndSortRequests(String name, String sortStrategy) {
        // 1. Xác định hướng sắp xếp dựa trên tham số sortStrategy
        Sort sort = "oldest".equals(sortStrategy)
                ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();

        // 2. Nếu không có tên thì trả về tất cả kèm sắp xếp (Dùng đúng biến 'repository')
        if (name == null || name.trim().isEmpty()) {
            return repository.findAll(sort);
        }

        // 3. Nếu có tên thì lọc theo tên bác sĩ (Dùng đúng biến 'repository')
        return repository.findByDentistFullNameContainingIgnoreCase(name, sort);
    }
}