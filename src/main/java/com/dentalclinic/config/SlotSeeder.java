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

            logger.info("Seeding/repairing slots for next {} days with capacity {}", DAYS_TO_SEED, DEFAULT_CAPACITY);

            int totalSlotsCreated = 0;
            int totalSlotsUpdated = 0;

            for (int day = 0; day < DAYS_TO_SEED; day++) {
                LocalDate date = today.plusDays(day);

                // Bỏ qua Chủ Nhật
                if (date.getDayOfWeek().getValue() == 7) {
                    continue;
                }

                LocalDateTime current = LocalDateTime.of(date, CLINIC_OPEN_TIME);
                LocalDateTime end = LocalDateTime.of(date, CLINIC_CLOSE_TIME);

                while (current.isBefore(end)) {
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
                }
            }
            logger.info("Slot seeding completed. Created={}, Updated={}", totalSlotsCreated, totalSlotsUpdated);
        };
    }

    /**
     * Phương thức bổ sung để vá các slot bị thiếu trong một khoảng thời gian cụ thể.
     */
    public void fillMissingLastSlots(SlotRepository slotRepository, LocalDate fromDate, LocalDate toDate) {
        if (fromDate == null || toDate == null) {
            return;
        }

        logger.info("Checking and filling missing slots from {} to {}...", fromDate, toDate);

        LocalDate currentDay = fromDate;
        int slotsAdded = 0;

        while (!currentDay.isAfter(toDate)) {
            if (currentDay.getDayOfWeek().getValue() != 7) {
                LocalDateTime dayStart = LocalDateTime.of(currentDay, CLINIC_OPEN_TIME);
                LocalDateTime dayEnd = LocalDateTime.of(currentDay, CLINIC_CLOSE_TIME);

                LocalDateTime slotTime = dayStart;
                while (slotTime.isBefore(dayEnd)) {
                    Optional<Slot> existingSlot = slotRepository.findBySlotTime(slotTime);

                    if (existingSlot.isEmpty()) {
                        slotRepository.save(new Slot(slotTime, DEFAULT_CAPACITY));
                        slotsAdded++;
                    } else if (!existingSlot.get().isActive()) {
                        Slot slot = existingSlot.get();
                        slot.setActive(true);
                        slot.setCapacity(DEFAULT_CAPACITY);
                        slotRepository.save(slot);
                    }
                    slotTime = slotTime.plusMinutes(30);
                }
            }
            currentDay = currentDay.plusDays(1);
        }
        logger.info("Repair complete. Added/Fixed {} slots.", slotsAdded);
    }
}