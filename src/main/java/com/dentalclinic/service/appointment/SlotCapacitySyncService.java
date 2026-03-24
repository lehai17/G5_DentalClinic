package com.dentalclinic.service.appointment;

import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.schedule.DentistSchedule;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.DentistScheduleRepository;
import com.dentalclinic.repository.SlotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SlotCapacitySyncService {
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);
    private static final LocalTime LUNCH_START = LocalTime.of(12, 0);
    private static final LocalTime LUNCH_END = LocalTime.of(13, 0);
    private static final int SLOT_MINUTES = 30;

    private final SlotRepository slotRepository;
    private final DentistScheduleRepository dentistScheduleRepository;
    private final DentistBusyScheduleRepository dentistBusyScheduleRepository;
    private final DentistProfileRepository dentistProfileRepository;

    public SlotCapacitySyncService(SlotRepository slotRepository,
                                   DentistScheduleRepository dentistScheduleRepository,
                                   DentistBusyScheduleRepository dentistBusyScheduleRepository,
                                   DentistProfileRepository dentistProfileRepository) {
        this.slotRepository = slotRepository;
        this.dentistScheduleRepository = dentistScheduleRepository;
        this.dentistBusyScheduleRepository = dentistBusyScheduleRepository;
        this.dentistProfileRepository = dentistProfileRepository;
    }

    @Transactional
    public void syncCapacities(LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate)) {
            return;
        }

        LocalDateTime rangeStart = LocalDateTime.of(fromDate, OPEN);
        LocalDateTime rangeEnd = LocalDateTime.of(toDate.plusDays(1), LocalTime.MIDNIGHT);
        List<Slot> slots = slotRepository.findAllSlotsInRange(rangeStart, rangeEnd);
        if (slots == null || slots.isEmpty()) {
            return;
        }

        Map<LocalDate, List<Slot>> slotsByDate = slots.stream()
                .filter(slot -> slot.getSlotTime() != null)
                .collect(Collectors.groupingBy(slot -> slot.getSlotTime().toLocalDate()));

        for (LocalDate date = fromDate; !date.isAfter(toDate); date = date.plusDays(1)) {
            List<Slot> daySlots = slotsByDate.get(date);
            if (daySlots == null || daySlots.isEmpty()) {
                continue;
            }

            List<DentistSchedule> schedules = dentistScheduleRepository.findEffectiveSchedulesForDate(
                    date,
                    date.getDayOfWeek(),
                    UserStatus.ACTIVE);
            Set<Long> busyDentistIds = new HashSet<>(dentistBusyScheduleRepository.findApprovedDentistIdsByDate(date));
            int fallbackDailyCapacity = countActiveDentistsForDate(date);

            boolean changed = false;
            for (Slot slot : daySlots) {
                int computedCapacity = calculateCapacityForSlot(slot, schedules, busyDentistIds, fallbackDailyCapacity);
                int normalizedCapacity = Math.max(computedCapacity, slot.getBookedCount());
                if (slot.getCapacity() != normalizedCapacity) {
                    slot.setCapacity(normalizedCapacity);
                    changed = true;
                }
            }

            if (changed) {
                slotRepository.saveAll(daySlots);
            }
        }
    }

    @Transactional
    public void syncAllFutureCapacities() {
        LocalDate today = LocalDate.now();
        LocalDateTime latestSlotTime = slotRepository.findLatestSlotTimeFrom(today.atStartOfDay());
        if (latestSlotTime == null) {
            return;
        }
        syncCapacities(today, latestSlotTime.toLocalDate());
    }

    private int calculateCapacityForSlot(Slot slot,
                                         List<DentistSchedule> schedules,
                                         Set<Long> busyDentistIds,
                                         int fallbackDailyCapacity) {
        if (slot == null || slot.getSlotTime() == null) {
            return 0;
        }

        LocalTime slotStart = slot.getSlotTime().toLocalTime();
        LocalTime slotEnd = slotStart.plusMinutes(SLOT_MINUTES);
        if (!isBookableClinicSlot(slotStart, slotEnd)) {
            return 0;
        }

        int availableDentists = 0;
        if (schedules != null && !schedules.isEmpty()) {
            for (DentistSchedule schedule : schedules) {
                if (schedule == null || schedule.getDentist() == null || schedule.getDentist().getId() == null) {
                    continue;
                }
                if (busyDentistIds.contains(schedule.getDentist().getId())) {
                    continue;
                }
                if (coversSlot(schedule, slotStart, slotEnd)) {
                    availableDentists++;
                }
            }
        }

        return Math.max(availableDentists, fallbackDailyCapacity);
    }

    private int countActiveDentistsForDate(LocalDate date) {
        List<DentistProfile> dentists = dentistProfileRepository.findAvailableDentistsForDate(date);
        return dentists != null ? dentists.size() : 0;
    }

    private boolean isBookableClinicSlot(LocalTime slotStart, LocalTime slotEnd) {
        if (slotStart.isBefore(OPEN) || slotEnd.isAfter(CLOSE) || !slotEnd.isAfter(slotStart)) {
            return false;
        }
        return slotStart.isBefore(LUNCH_START) || !slotStart.isBefore(LUNCH_END);
    }

    private boolean coversSlot(DentistSchedule schedule, LocalTime slotStart, LocalTime slotEnd) {
        LocalTime startTime = schedule.getStartTime();
        LocalTime endTime = schedule.getEndTime();
        if (startTime == null || endTime == null) {
            return false;
        }
        return !startTime.isAfter(slotStart) && !endTime.isBefore(slotEnd);
    }
}


