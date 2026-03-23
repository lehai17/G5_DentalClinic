package com.dentalclinic.service.admin;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.BusySchedule;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdminBusyScheduleService {

    private final DentistBusyScheduleRepository repository;
    private final DentistProfileRepository dentistProfileRepository;
    private final NotificationService notificationService;

    public AdminBusyScheduleService(DentistBusyScheduleRepository repository,
                                    DentistProfileRepository dentistProfileRepository,
                                    NotificationService notificationService) {
        this.repository = repository;
        this.dentistProfileRepository = dentistProfileRepository;
        this.notificationService = notificationService;
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

        if (status != null) {
            String normalized = status.trim().toUpperCase();
            if ("APPROVED".equals(normalized)) {
                notificationService.notifyDentistBusyScheduleApproved(request);
            } else if ("REJECTED".equals(normalized)) {
                notificationService.notifyDentistBusyScheduleRejected(request);
            }
        }
    }

    @Transactional
    public void submitBusyRequest(Long dentistId, LocalDate start, LocalDate end, String reason) {
        validateBusyRequest(dentistId, start, end, reason, null);

        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Bác sĩ không tồn tại"));

        BusySchedule busy = new BusySchedule();
        busy.setDentist(dentist);
        busy.setStartDate(start);
        busy.setEndDate(end);
        busy.setReason(reason.trim());
        busy.setStatus("PENDING");

        repository.save(busy);
    }

    @Transactional
    public void updateBusyRequest(Long requestId, Long dentistId, LocalDate start, LocalDate end, String reason) {
        BusySchedule busy = repository.findByIdAndDentistId(requestId, dentistId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn xin nghỉ."));

        if (!"PENDING".equalsIgnoreCase(busy.getStatus())) {
            throw new IllegalArgumentException("Chỉ có thể cập nhật đơn đang chờ duyệt.");
        }

        validateBusyRequest(dentistId, start, end, reason, requestId);

        busy.setStartDate(start);
        busy.setEndDate(end);
        busy.setReason(reason.trim());
        repository.save(busy);
    }

    @Transactional
    public void deleteBusyRequest(Long requestId, Long dentistId) {
        BusySchedule busy = repository.findByIdAndDentistId(requestId, dentistId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy đơn xin nghỉ."));

        if (!"PENDING".equalsIgnoreCase(busy.getStatus())) {
            throw new IllegalArgumentException("Chỉ có thể xóa đơn đang chờ duyệt.");
        }

        repository.delete(busy);
    }

    public List<BusySchedule> getRequestsByDentist(Long dentistId) {
        return repository.findByDentistIdOrderByCreatedAtDesc(dentistId);
    }

    public List<BusySchedule> searchAndSortRequests(String name, String sortStrategy) {
        Sort sort = "oldest".equals(sortStrategy)
                ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();

        if (name == null || name.trim().isEmpty()) {
            return repository.findAll(sort);
        }

        return repository.findByDentistFullNameContainingIgnoreCase(name, sort);
    }

    private void validateBusyRequest(Long dentistId,
                                     LocalDate start,
                                     LocalDate end,
                                     String reason,
                                     Long excludeRequestId) {
        if (start == null) {
            throw new IllegalArgumentException("Vui lòng chọn ngày bắt đầu.");
        }
        if (end == null) {
            throw new IllegalArgumentException("Vui lòng chọn ngày kết thúc.");
        }
        if (reason == null || reason.trim().isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập lý do xin nghỉ.");
        }

        LocalDate firstDayOfMonth = start.withDayOfMonth(1);
        LocalDate lastDayOfMonth = start.withDayOfMonth(start.lengthOfMonth());

        long count = excludeRequestId == null
                ? repository.countLeavesInMonth(dentistId, firstDayOfMonth, lastDayOfMonth)
                : repository.countLeavesInMonthExcludingId(excludeRequestId, dentistId, firstDayOfMonth, lastDayOfMonth);
        if (count >= 2) {
            throw new RuntimeException("Bác sĩ đã dùng hết giới hạn nghỉ trong tháng " + start.getMonthValue() + " (tối đa 2 lần/tháng).");
        }

        long overlappingApproved = excludeRequestId == null
                ? repository.countOverlappingRequestsByStatus(dentistId, start, end, "APPROVED")
                : repository.countOverlappingRequestsByStatusExcludingId(excludeRequestId, dentistId, start, end, "APPROVED");
        if (overlappingApproved > 0) {
            throw new IllegalArgumentException("Khoảng thời gian này đã được duyệt nghỉ trước đó.");
        }

        long overlappingPending = excludeRequestId == null
                ? repository.countOverlappingRequestsByStatus(dentistId, start, end, "PENDING")
                : repository.countOverlappingRequestsByStatusExcludingId(excludeRequestId, dentistId, start, end, "PENDING");
        if (overlappingPending > 0) {
            throw new IllegalArgumentException("Khoảng thời gian này đã tồn tại trong đơn xin nghỉ khác.");
        }
    }
}
