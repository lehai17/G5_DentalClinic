package com.dentalclinic.service.admin;

import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.BusySchedule;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class AdminBusyScheduleService {

    private final DentistBusyScheduleRepository repository;
    private final DentistProfileRepository dentistProfileRepository;

    public AdminBusyScheduleService(DentistBusyScheduleRepository repository,
                                    DentistProfileRepository dentistProfileRepository) {
        this.repository = repository;
        this.dentistProfileRepository = dentistProfileRepository;
    }

    public List<BusySchedule> getAllRequests() {
        return repository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public void updateStatus(Long requestId, String status) {
        BusySchedule request = repository.findById(requestId)
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay don yeu cau voi ID: " + requestId));

        // Busy schedule da duoc tinh trong query tim dentist available,
        // khong duoc khoa slot global vi se anh huong huyl/booking cua customer.
        request.setStatus(status);
        repository.save(request);
    }

    @Transactional
    public void submitBusyRequest(Long dentistId, LocalDate start, LocalDate end, String reason) {
        LocalDate firstDayOfMonth = start.withDayOfMonth(1);
        LocalDate lastDayOfMonth = start.withDayOfMonth(start.lengthOfMonth());

        long count = repository.countLeavesInMonth(dentistId, firstDayOfMonth, lastDayOfMonth);
        if (count >= 2) {
            throw new RuntimeException("Bac si da dung het gioi han nghi trong thang " + start.getMonthValue() + " (toi da 2 lan/thang)!");
        }

        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Bac si khong ton tai"));

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
        Sort sort = "oldest".equals(sortStrategy)
                ? Sort.by("createdAt").ascending()
                : Sort.by("createdAt").descending();

        if (name == null || name.trim().isEmpty()) {
            return repository.findAll(sort);
        }

        return repository.findByDentistFullNameContainingIgnoreCase(name, sort);
    }
}
