package com.dentalclinic.config;

import com.dentalclinic.model.appointment.Slot;
import com.dentalclinic.repository.SlotRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

/**
 * Seed slots cho hệ thống đặt lịch mới.
 * Mỗi ngày có 18 slots (08:00 - 17:00, mỗi slot 30 phút).
 */
@Configuration
public class SlotSeeder {

    private static final Logger logger = LoggerFactory.getLogger(SlotSeeder.class);

    private static final LocalTime CLINIC_OPEN_TIME = LocalTime.of(8, 0);
    private static final LocalTime CLINIC_CLOSE_TIME = LocalTime.of(17, 0);
    private static final int DEFAULT_CAPACITY = 3;
    private static final int DAYS_TO_SEED = 30;

    @Bean
    public ApplicationRunner seedSlotsIfNeeded(SlotRepository slotRepository) {
        return (ApplicationArguments args) -> {
            LocalDate today = LocalDate.now();
            LocalDate endDate = today.plusDays(DAYS_TO_SEED - 1);

            logger.info("Seeding/repairing slots for next {} days with capacity {}", DAYS_TO_SEED, DEFAULT_CAPACITY);

            int totalSlotsCreated = 0;
            int totalSlotsUpdated = 0;

            for (int day = 0; day < DAYS_TO_SEED; day++) {
                LocalDate date = today.plusDays(day);

                if (date.getDayOfWeek().getValue() == 7) {
                    continue;
                }

                LocalDateTime current = LocalDateTime.of(date, CLINIC_OPEN_TIME);
                LocalDateTime end = LocalDateTime.of(date, CLINIC_CLOSE_TIME);

                while (current.isBefore(end) || current.equals(end.minusMinutes(30))) {
                    Optional<Slot> existing = slotRepository.findBySlotTime(current);
                    if (existing.isPresent()) {
                        Slot slot = existing.get();
                        boolean changed = false;

                        if (!slot.isActive()) {
                            slot.setActive(true);
                            changed = true;
                        }
                        if (slot.getCapacity() != DEFAULT_CAPACITY) {
                            slot.setCapacity(DEFAULT_CAPACITY);
                            changed = true;
                        }
                        if (slot.getBookedCount() > slot.getCapacity()) {
                            slot.setBookedCount(slot.getCapacity());
                            changed = true;
                        }

                        if (changed) {
                            slotRepository.save(slot);
                            totalSlotsUpdated++;
                        }
                    } else {
                        slotRepository.save(new Slot(current, DEFAULT_CAPACITY));
                        totalSlotsCreated++;
                    }

                    current = current.plusMinutes(30);
                } // Kết thúc vòng lặp while
            } // Kết thúc vòng lặp for

            logger.info("Slot seeding done. Created={}, Updated={}", totalSlotsCreated, totalSlotsUpdated);

            logger.info("Slot seed completed. created={}, updated={}", totalSlotsCreated, totalSlotsUpdated);
        };
    }

    /**
     * This method used to patch legacy missing slots, kept as no-op for compatibility.
     */
    @SuppressWarnings("unused")
    private void fillMissingLastSlots(SlotRepository slotRepository, LocalDate fromDate, LocalDate toDate) {
        // No-op: seeding now does full upsert for all expected slot_time values.
        logger.debug("fillMissingLastSlots is deprecated; full upsert seeding is used instead.");
        if (fromDate == null || toDate == null) {
            return;
        logger.info("Checking and filling missing slots (all incomplete dates)...");

        LocalDate currentDay = fromDate;
        int slotsAdded = 0;

        while (!currentDay.isAfter(toDate)) {
            if (currentDay.getDayOfWeek().getValue() != 7) { // Bỏ qua Chủ Nhật
                LocalDateTime dayStart = LocalDateTime.of(currentDay, CLINIC_OPEN_TIME);
                LocalDateTime dayEnd = LocalDateTime.of(currentDay, CLINIC_CLOSE_TIME);

                // Tìm tất cả slot hiện có trong ngày
                List<Slot> existingDaySlots = slotRepository.findAllSlotsBetweenTimes(dayStart, dayEnd);

                // Nếu không đủ 18 slots, tiến hành kiểm tra từng slot một
                if (existingDaySlots.size() < 18) {
                    logger.warn("Day {} only has {} slots, expected 18. Repairing...", currentDay, existingDaySlots.size());

                    LocalDateTime slotTime = dayStart;
                    while (slotTime.isBefore(dayEnd)) {
                        Optional<Slot> existingSlot = slotRepository.findBySlotTime(slotTime);

                        if (existingSlot.isEmpty()) {
                            Slot slot = new Slot(slotTime, DEFAULT_CAPACITY);
                            slotRepository.save(slot);
                            slotsAdded++;
                            logger.info("Created missing slot: {}", slotTime);
                        } else if (!existingSlot.get().isActive()) {
                            Slot slot = existingSlot.get();
                            slot.setActive(true);
                            if (slot.getCapacity() <= 0) {
                                slot.setCapacity(DEFAULT_CAPACITY);
                            }
                            slotRepository.save(slot);
                            logger.info("Re-activated slot: {}", slotTime);
                        }
                        slotTime = slotTime.plusMinutes(30);
                    }
                }
            }
            currentDay = currentDay.plusDays(1);
        }

        if (slotsAdded > 0) {
            logger.info("Added {} missing slots total", slotsAdded);
        } else {
            logger.info("No missing slots found, database is complete");
        }
    }
}