package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.AdminAgendaItemDto;
import com.dentalclinic.dto.admin.AdminGridEventDto;
import com.dentalclinic.dto.admin.SlotDayBadgeDto;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.DentistScheduleRepository;
import com.dentalclinic.repository.SlotRepository;
import com.dentalclinic.service.appointment.SlotCapacitySyncService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminSlotService {
    private static final LocalTime OPEN = LocalTime.of(8, 0);
    private static final LocalTime CLOSE = LocalTime.of(17, 0);
    private static final int SLOT_MIN = 30;

    private final SlotRepository slotRepository;
    private final AppointmentRepository appointmentRepository;
    private final DentistProfileRepository dentistProfileRepository;
    private final DentistScheduleRepository dentistScheduleRepository;
    private final SlotCapacitySyncService slotCapacitySyncService;

    public AdminSlotService(SlotRepository slotRepository,
            SlotCapacitySyncService slotCapacitySyncService,
            AppointmentRepository appointmentRepository,
            DentistProfileRepository dentistProfileRepository,
            DentistScheduleRepository dentistScheduleRepository) {
        this.slotRepository = slotRepository;
        this.slotCapacitySyncService = slotCapacitySyncService;
        this.appointmentRepository = appointmentRepository;
        this.dentistProfileRepository = dentistProfileRepository;
        this.dentistScheduleRepository = dentistScheduleRepository;
    }

    public static List<LocalTime> buildHalfHourTimes() {
        List<LocalTime> times = new ArrayList<>();
        // Morning: 08:00 - 12:00
        LocalTime t = OPEN;
        while (!t.isAfter(LocalTime.of(11, 30))) {
            times.add(t);
            t = t.plusMinutes(SLOT_MIN);
        }
        // Afternoon: 13:00 - 17:00
        t = LocalTime.of(13, 0);
        while (!t.isAfter(CLOSE.minusMinutes(SLOT_MIN))) {
            times.add(t);
            t = t.plusMinutes(SLOT_MIN);
        }
        return times;
    }

    public List<SlotDayBadgeDto> getMonthBadges(YearMonth ym) {
        return getBadgesInRange(ym.atDay(1), ym.atEndOfMonth());
    }

    public List<SlotDayBadgeDto> getBadgesInRange(LocalDate start, LocalDate end) {
        List<SlotDayBadgeDto> badges = new ArrayList<>();
        int slotsPerDay = buildHalfHourTimes().size();

        List<AppointmentStatus> activeStatuses = List.of(
                AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN,
                AppointmentStatus.EXAMINING,
                AppointmentStatus.COMPLETED, AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.WAITING_PAYMENT, AppointmentStatus.REEXAM,
                AppointmentStatus.PENDING);

        // Fetch all active appointments in range
        List<Appointment> allApps = appointmentRepository.findByDateBetweenAndStatusIn(start, end, activeStatuses);
        Map<LocalDate, Integer> appCountMap = new HashMap<>();
        if (allApps != null) {
            for (Appointment a : allApps) {
                if (a != null && a.getDate() != null) {
                    appCountMap.merge(a.getDate(), 1, Integer::sum);
                }
            }
        }

        // Fetch all slots in range to calculate day-by-day active capacity in DB
        LocalDateTime rangeStart = start.atStartOfDay();
        LocalDateTime rangeEnd = end.atTime(LocalTime.MAX);
        List<Slot> allSlots = slotRepository.findAllSlotsInRange(rangeStart, rangeEnd);

        Map<LocalDate, Long> slotCapacityMap = new HashMap<>(); // Only active ones
        Map<LocalDate, Boolean> dayHasSlotsMap = new HashMap<>();
        Map<LocalDate, Boolean> dayHasActiveSlotsMap = new HashMap<>();

        if (allSlots != null) {
            for (Slot s : allSlots) {
                if (s == null || s.getSlotTime() == null)
                    continue;
                LocalDate ld = s.getSlotTime().toLocalDate();
                dayHasSlotsMap.put(ld, true);
                if (s.isActive()) {
                    dayHasActiveSlotsMap.put(ld, true);
                    slotCapacityMap.merge(ld, (long) s.getCapacity(), Long::sum);
                }
            }
        }

        // Cache available dentists for each day
        Map<LocalDate, Integer> dentistCountMap = new HashMap<>();
        LocalDate d_iter = start;
        while (!d_iter.isAfter(end)) {
            List<DentistProfile> activeDentists = dentistProfileRepository.findAvailableDentistsWithSchedule(d_iter,
                    d_iter.getDayOfWeek());
            dentistCountMap.put(d_iter, activeDentists != null ? activeDentists.size() : 0);
            d_iter = d_iter.plusDays(1);
        }

        LocalDate d = start;
        while (!d.isAfter(end)) {
            long activeCapacityInDb = slotCapacityMap.getOrDefault(d, 0L);
            int booked = appCountMap.getOrDefault(d, 0);
            int totalCapacity;

            if (activeCapacityInDb <= 0) {
                totalCapacity = 0;
            } else {
                int dentistCount = dentistCountMap.getOrDefault(d, 0);
                if (dentistCount <= 0) {
                    totalCapacity = (int) activeCapacityInDb;
                } else {
                    totalCapacity = Math.min((int) activeCapacityInDb, dentistCount * slotsPerDay);
                }
            }

            SlotDayBadgeDto badge = new SlotDayBadgeDto(d, booked, totalCapacity);

            // Logic: CLOSED ONLY if it has slots AND none are active
            boolean hasSlots = dayHasSlotsMap.getOrDefault(d, false);
            boolean hasActive = dayHasActiveSlotsMap.getOrDefault(d, false);
            if (hasSlots && !hasActive) {
                badge.setActive(false);
            } else if (!hasSlots && d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                badge.setActive(false);
            } else {
                badge.setActive(true);
            }

            badge.calculateDensity();
            badges.add(badge);
            d = d.plusDays(1);
        }
        return badges;
    }

    public List<List<com.dentalclinic.dto.admin.CalendarCellDto>> buildMonthCalendar(YearMonth ym) {
        if (ym == null)
            ym = YearMonth.now();

        LocalDate first = ym.atDay(1);
        LocalDate start = first;
        while (start.getDayOfWeek() != DayOfWeek.MONDAY) {
            start = start.minusDays(1);
        }

        List<List<com.dentalclinic.dto.admin.CalendarCellDto>> weeks = new ArrayList<>();
        LocalDate cursor = start;

        for (int w = 0; w < 6; w++) {
            List<com.dentalclinic.dto.admin.CalendarCellDto> week = new ArrayList<>();
            for (int d = 0; d < 7; d++) {
                boolean inMonth = cursor.getMonth() == ym.getMonth();
                week.add(new com.dentalclinic.dto.admin.CalendarCellDto(cursor, inMonth));
                cursor = cursor.plusDays(1);
            }
            weeks.add(week);
        }
        return weeks;
    }

    public SlotDayBadgeDto getTodaySummary(LocalDate date) {
        int slotsPerDay = buildHalfHourTimes().size();

        // Use same refined logic as the calendar badges
        LocalDateTime dayStart = LocalDateTime.of(date, OPEN);
        LocalDateTime dayEnd = LocalDateTime.of(date, CLOSE);

        Long activeCapacityInDbLong = slotRepository.sumCapacityActiveInRange(dayStart, dayEnd);
        long activeCapacityInDb = (activeCapacityInDbLong != null) ? activeCapacityInDbLong : 0L;

        int totalCapacity;
        int booked = 0;

        List<AppointmentStatus> activeStatuses = List.of(
                AppointmentStatus.CONFIRMED, AppointmentStatus.CHECKED_IN,
                AppointmentStatus.EXAMINING,
                AppointmentStatus.COMPLETED, AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.WAITING_PAYMENT, AppointmentStatus.REEXAM,
                AppointmentStatus.PENDING);

        if (activeCapacityInDb <= 0) {
            totalCapacity = 0;
        } else {
            List<DentistProfile> activeDentists = dentistProfileRepository.findAvailableDentistsWithSchedule(date,
                    date.getDayOfWeek());
            if (activeDentists.isEmpty()) {
                totalCapacity = (int) activeCapacityInDb;
            } else {
                totalCapacity = activeDentists.size() * slotsPerDay;
            }
            // Count appointments directly
            List<Appointment> apps = appointmentRepository.findByDateAndStatusIn(date, activeStatuses);
            booked = (apps != null) ? apps.size() : 0;
        }

        SlotDayBadgeDto summary = new SlotDayBadgeDto(date, booked, totalCapacity);

        List<Slot> todaySlots = slotRepository.findAllSlotsInRange(dayStart, dayEnd);
        boolean hasSlots = (todaySlots != null && !todaySlots.isEmpty());
        boolean hasActive = activeCapacityInDb > 0;

        if (hasSlots && !hasActive) {
            summary.setActive(false);
        } else if (!hasSlots && date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            summary.setActive(false);
        } else {
            summary.setActive(true);
        }

        summary.calculateDensity();
        return summary;
    }

    public List<DentistProfile> getAllActiveDentistsForDate(LocalDate date) {
        // reuse existing repository helper that excludes approved busy schedules
        return dentistProfileRepository.findAvailableDentistsForDate(date);
    }

    public List<AdminAgendaItemDto> getAgenda(LocalDate date, Long dentistId) {
        List<AppointmentStatus> statuses = List.of(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.CHECKED_IN,
                AppointmentStatus.EXAMINING,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.WAITING_PAYMENT,
                AppointmentStatus.REEXAM,
                AppointmentStatus.PENDING);

        List<Appointment> list = appointmentRepository.findAgendaWithDetails(date, statuses, dentistId);

        List<AdminAgendaItemDto> out = new ArrayList<>();
        if (list != null) {
            for (Appointment a : list) {
                AdminAgendaItemDto dto = new AdminAgendaItemDto(
                        a.getId(),
                        a.getStartTime(),
                        a.getEndTime(),
                        a.getCustomer() != null && a.getCustomer().getUser() != null ? a.getCustomer().getUser().getId()
                                : null,
                        a.getCustomer() != null ? a.getCustomer().getFullName() : null,
                        a.getDentist() != null ? a.getDentist().getId() : null,
                        a.getDentist() != null ? a.getDentist().getFullName() : null,
                        a.getService() != null ? a.getService().getName() : null,
                        a.getStatus() != null ? a.getStatus().name() : null);
                dto.setAvailable(false);
                out.add(dto);
            }
        }
        return out;
    }

    public AdminAgendaItemDto getAppointmentDetail(Long id) {
        Appointment a = appointmentRepository.findById(id).orElse(null);
        if (a == null)
            return null;

        AdminAgendaItemDto dto = new AdminAgendaItemDto(
                a.getId(),
                a.getStartTime(),
                a.getEndTime(),
                a.getCustomer() != null && a.getCustomer().getUser() != null ? a.getCustomer().getUser().getId() : null,
                a.getCustomer() != null ? a.getCustomer().getFullName() : null,
                a.getDentist() != null ? a.getDentist().getId() : null,
                a.getDentist() != null ? a.getDentist().getFullName() : null,
                a.getService() != null ? a.getService().getName() : null,
                a.getStatus() != null ? a.getStatus().name() : null);
        dto.setAvailable(false);
        return dto;
    }

    public Map<String, AdminGridEventDto> buildDailyDentistGridEventMap(LocalDate date, List<DentistProfile> dentists) {
        List<AdminGridEventDto> events = buildDailyEvents(date);
        Map<String, AdminGridEventDto> map = new HashMap<>();
        if (events != null) {
            for (AdminGridEventDto ev : events) {
                if (ev == null || ev.getDate() == null || ev.getStartTime() == null)
                    continue;
                // Include unassigned appointments by using a special key prefix
                String dentistPart = (ev.getDentistId() == null) ? "UNASSIGNED" : ev.getDentistId().toString();
                String key = dentistPart + "_" + ev.getDate().toString() + "_" + formatHHmm(ev.getStartTime());
                map.put(key, ev);
            }
        }
        if (dentists != null && !dentists.isEmpty()) {
            Map<Long, Boolean> allowed = new HashMap<>();
            for (DentistProfile dp : dentists) {
                if (dp != null && dp.getId() != null) {
                    allowed.put(dp.getId(), true);
                }
            }
            map.entrySet().removeIf(e -> {
                try {
                    String k = e.getKey();
                    if (k == null || !k.contains("_"))
                        return true;
                    String part = k.substring(0, k.indexOf('_'));
                    if ("UNASSIGNED".equals(part))
                        return false;
                    Long id = Long.parseLong(part);
                    return !allowed.containsKey(id);
                } catch (Exception ex) {
                    return false; // Keep it if we can't parse it, better than crashing
                }
            });
        }
        return map;
    }

    public Map<String, AdminGridEventDto> buildWeeklyDayGridEventMap(LocalDate weekStart, Long dentistId) {
        LocalDate weekEnd = weekStart.plusDays(6);
        List<AdminGridEventDto> events = buildEventsInRange(weekStart, weekEnd, dentistId);
        Map<String, AdminGridEventDto> map = new HashMap<>();
        if (events != null) {
            for (AdminGridEventDto ev : events) {
                if (ev != null && ev.getDate() != null && ev.getStartTime() != null) {
                    String key = ev.getDate().toString() + "_" + formatHHmm(ev.getStartTime());
                    map.put(key, ev);
                }
            }
        }
        return map;
    }

    public List<AdminGridEventDto> buildDailyEvents(LocalDate date) {
        return buildEventsInRange(date, date, null);
    }

    private List<AdminGridEventDto> buildEventsInRange(LocalDate start, LocalDate end, Long dentistId) {
        List<AppointmentStatus> statuses = List.of(
                AppointmentStatus.CONFIRMED,
                AppointmentStatus.CHECKED_IN,
                AppointmentStatus.EXAMINING,
                AppointmentStatus.COMPLETED,
                AppointmentStatus.IN_PROGRESS,
                AppointmentStatus.WAITING_PAYMENT,
                AppointmentStatus.REEXAM,
                AppointmentStatus.PENDING);

        List<AdminGridEventDto> out = new ArrayList<>();

        // Bulk fetch appointments
        List<Appointment> allApps = appointmentRepository.findByDateBetweenAndStatusIn(start, end, statuses);
        if (allApps != null) {
            for (Appointment a : allApps) {
                if (a == null)
                    continue;
                if (dentistId != null) {
                    if (a.getDentist() == null || a.getDentist().getId() == null
                            || !dentistId.equals(a.getDentist().getId())) {
                        continue;
                    }
                }
                int span = calcSpan(a.getStartTime(), a.getEndTime());
                out.add(new AdminGridEventDto(
                        a.getId(),
                        a.getCustomer() != null && a.getCustomer().getUser() != null
                                ? a.getCustomer().getUser().getId()
                                : null,
                        a.getCustomer() != null ? a.getCustomer().getFullName() : null,
                        a.getService() != null ? a.getService().getName() : null,
                        a.getDentist() != null ? a.getDentist().getId() : null,
                        a.getDentist() != null ? a.getDentist().getFullName() : null,
                        a.getDate(),
                        a.getStartTime(),
                        a.getEndTime(),
                        a.getStatus() != null ? a.getStatus().name() : null,
                        span));
            }
        }

        // Bulk fetch slots (including inactive/locked ones)
        LocalDateTime rangeStart = LocalDateTime.of(start, OPEN);
        LocalDateTime rangeEnd = LocalDateTime.of(end, CLOSE);
        List<Slot> allSlots = slotRepository.findAllSlotsInRange(rangeStart, rangeEnd);
        if (allSlots != null) {
            for (Slot s : allSlots) {
                if (s != null && !s.isActive() && s.getSlotTime() != null) {
                    out.add(new AdminGridEventDto(
                            null, null, "LOCKED", "Khóa hành chính",
                            null, null, s.getSlotTime().toLocalDate(), s.getStartTime(),
                            s.getEndTime(),
                            "LOCKED", 1));
                }
            }
        }

        return out;
    }

    private int calcSpan(LocalTime start, LocalTime end) {
        if (start == null || end == null)
            return 1;
        long mins = java.time.Duration.between(start, end).toMinutes();
        if (mins <= 0)
            return 1;
        return (int) Math.ceil((double) mins / SLOT_MIN);
    }

    private String formatHHmm(LocalTime t) {
        return t == null ? "" : String.format(Locale.ROOT, "%02d:%02d", t.getHour(), t.getMinute());
    }

    @Transactional
    public int generateSlots(LocalDate fromDate, LocalDate toDate, int capacity) {
        if (fromDate == null || toDate == null || fromDate.isAfter(toDate))
            return 0;
        int cap = Math.max(1, capacity);
        int created = 0;

        // Pre-fetch all existing slots in the range to avoid N+1
        LocalDateTime rangeStart = LocalDateTime.of(fromDate, OPEN);
        LocalDateTime rangeEnd = LocalDateTime.of(toDate, CLOSE);
        List<Slot> allExisting = slotRepository.findAllSlotsInRange(rangeStart, rangeEnd);
        Map<LocalDateTime, Slot> slotMap = new HashMap<>();
        if (allExisting != null) {
            for (Slot s : allExisting) {
                if (s != null && s.getSlotTime() != null) {
                    slotMap.put(s.getSlotTime(), s);
                }
            }
        }

        LocalDate d = fromDate;
        while (!d.isAfter(toDate)) {
            LocalDateTime cur = LocalDateTime.of(d, OPEN);
            LocalDateTime end = LocalDateTime.of(d, CLOSE);
            while (cur.isBefore(end)) {
                Slot slot = slotMap.get(cur);
                if (slot == null) {
                    Slot newSlot = new Slot(cur, cap);
                    if (d.getDayOfWeek() == DayOfWeek.SUNDAY) {
                        newSlot.setActive(false);
                        newSlot.setLockReason("Nghỉ Chủ Nhật");
                    }
                    slotRepository.save(newSlot);
                    created++;
                } else {
                    boolean changed = false;
                    // Proactively lock Sundays even if they already exist, if they have no bookings
                    if (d.getDayOfWeek() == java.time.DayOfWeek.SUNDAY && slot.isActive()
                            && slot.getBookedCount() == 0) {
                        slot.setActive(false);
                        slot.setLockReason("Nghỉ Chủ Nhật");
                        changed = true;
                    }
                    if (slot.getCapacity() != cap) {
                        slot.setCapacity(cap);
                        changed = true;
                    }
                    if (changed) {
                        slotRepository.save(slot);
                    }
                }
                cur = cur.plusMinutes(SLOT_MIN);
            }
            d = d.plusDays(1);
        }
        slotCapacitySyncService.syncCapacities(fromDate, toDate);
        return created;
    }

    @Transactional
    public void lockSlots(LocalDateTime start, LocalDateTime end, String reason) {
        if (start == null || end == null)
            return;
        slotRepository.disableSlotsInPeriod(start, end, reason != null ? reason : "Administrative LOCK");
    }

    @Transactional
    public void unlockSlots(LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null)
            return;
        slotRepository.enableSlotsInPeriod(start, end);
    }

    @Transactional
    public void lockDay(LocalDate date, String reason) {
        // RULE 2: LEAD TIME (Min 24h)
        if (!date.isAfter(LocalDate.now())) {
            throw new RuntimeException(
                    "Chỉ có thể khóa các ngày trong tương lai (từ ngày mai trở đi).");
        }

        // RULE 1: NO ACTIVE APPOINTMENTS
        List<AppointmentStatus> activeStatuses = List.of(
                AppointmentStatus.CONFIRMED, AppointmentStatus.PENDING,
                AppointmentStatus.CHECKED_IN, AppointmentStatus.EXAMINING,
                AppointmentStatus.IN_PROGRESS, AppointmentStatus.WAITING_PAYMENT,
                AppointmentStatus.REEXAM);
        Long appCount = appointmentRepository.countByDateAndStatusIn(date, activeStatuses);
        if (appCount != null && appCount > 0) {
            throw new RuntimeException(
                    "Không thể khóa vì ngày này đang có " + appCount + " lịch hẹn chưa xử lý.");
        }

        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        // Ensure standard slots exist before locking (keeps UI consistent)
        List<Slot> existing = slotRepository.findAllSlotsInRange(start, end);
        if (existing == null || existing.isEmpty()) {
            generateSlots(date, date, 3); // Default capacity 3
        }

        lockSlots(start, end, reason);
    }

    @Transactional
    public void unlockDay(LocalDate date) {
        LocalDateTime start = date.atStartOfDay();
        LocalDateTime end = date.atTime(LocalTime.MAX);

        List<Slot> existing = slotRepository.findAllSlotsInRange(start, end);
        if (existing == null || existing.isEmpty()) {
            generateSlots(date, date, 3);
        }

        List<Slot> toUnlock = slotRepository.findAllSlotsInRange(start, end);
        for (Slot s : toUnlock) {
            s.setActive(true);
            s.setLockReason(null);
        }
        slotRepository.saveAll(toUnlock);
    }

    @Transactional
    public void resetAllSlots() {
        List<Slot> all = slotRepository.findAll();
        List<Slot> toSave = new ArrayList<>();
        for (Slot s : all) {
            if (s.getSlotTime() != null) {
                if (s.getSlotTime().getDayOfWeek() == DayOfWeek.SUNDAY) {
                    if (s.isActive()) {
                        s.setActive(false);
                        s.setLockReason("Nghỉ Chủ Nhật");
                        toSave.add(s);
                    }
                } else {
                    if (!s.isActive()) {
                        s.setActive(true);
                        s.setLockReason(null);
                        toSave.add(s);
                    }
                }
            }
        }
        if (!toSave.isEmpty()) {
            slotRepository.saveAll(toSave);
        }
    }

    @Transactional
    public void generateMonthlySchedule(java.time.YearMonth yearMonth) {
        LocalDate start = yearMonth.atDay(1);
        LocalDate end = yearMonth.atEndOfMonth();

        // 1. Sinh lịch Slot mặc định cho cả tháng đó
        generateSlots(start, end, 3);

        // 2. Lấy danh sách bác sĩ đang hoạt động
        List<DentistProfile> activeDentists = dentistProfileRepository.findAll().stream()
                .filter(d -> d.getUser() != null
                        && d.getUser().getStatus() == com.dentalclinic.model.user.UserStatus.ACTIVE)
                .toList();

        if (activeDentists.isEmpty())
            return;

        // 3. Quét từng Bác Sĩ qua các ng y
        LocalDate d = start;
        while (!d.isAfter(end)) {
            if (d.getDayOfWeek() != DayOfWeek.SUNDAY) {
                final LocalDate currentDay = d;
                for (DentistProfile dentist : activeDentists) {
                    List<com.dentalclinic.model.schedule.DentistSchedule> existing = dentistScheduleRepository
                            .findByDentist_IdAndDate(dentist.getId(), currentDay);
                    if (existing == null || existing.isEmpty()) {
                        long totalSaves = 0;
                        int[][] shifts = { { 8, 12 }, { 13, 17 } };
                        for (int[] shift : shifts) {
                            for (int hour = shift[0]; hour < shift[1]; hour++) {
                                com.dentalclinic.model.schedule.DentistSchedule s = new com.dentalclinic.model.schedule.DentistSchedule();
                                s.setDentist(dentist);
                                s.setDate(currentDay);
                                s.setDayOfWeek(currentDay.getDayOfWeek());
                                s.setStartTime(LocalTime.of(hour, 0));
                                s.setEndTime(LocalTime.of(hour + 1, 0));
                                s.setAvailable(true);
                                dentistScheduleRepository.save(s);
                                totalSaves++;
                            }
                        }
                    }
                }
            }
            d = d.plusDays(1);
        }
    }
}
