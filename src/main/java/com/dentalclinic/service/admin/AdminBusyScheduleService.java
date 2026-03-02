package com.dentalclinic.service.admin;

import com.dentalclinic.model.schedule.BusySchedule;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class AdminBusyScheduleService {

    private final DentistBusyScheduleRepository repository;
    private final SlotRepository slotRepository;

    public AdminBusyScheduleService(DentistBusyScheduleRepository repository, SlotRepository slotRepository) {
        this.repository = repository;
        this.slotRepository = slotRepository;
    }

    public List<BusySchedule> getAllRequests() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void updateStatus(Long requestId, String status) {
        BusySchedule request = repository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Request not found"));

        request.setStatus(status);
        repository.save(request);

        // Nếu duyệt (APPROVED), thực hiện khóa các Slot trong khoảng thời gian đó
        if ("APPROVED".equals(status)) {
            lockDentistSlots(request);
        }
    }

    private void lockDentistSlots(BusySchedule request) {
        // Chuyển đổi LocalDate sang LocalDateTime
        var startDateTime = request.getStartDate().atTime(8, 0);
        var endDateTime = request.getEndDate().atTime(17, 0);

        // Gọi hàm Repository đã sửa (chỉ truyền start và end)
        slotRepository.disableSlotsInPeriod(startDateTime, endDateTime);
    }
}