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
                    java.util.Optional<Slot> existing = slotRepository.findBySlotTime(current);
                    if (existing.isPresent()) {
                        Slot slot = existing.get();
                        boolean changed = false;
                        if (!slot.isActive()) {
                            slot.setActive(true);
                            changed = true;
                        }
                        if (slot.getCapacity() <= 0) {
                            slot.setCapacity(DEFAULT_CAPACITY);
                            changed = true;
                        }
                        if (changed) {
                            slotRepository.save(slot);
                            totalSlotsUpdated++;
                        }
                    } else {
                        Slot slot = new Slot(current, DEFAULT_CAPACITY);
                        slotRepository.save(slot);
                        totalSlotsCreated++;
                    }
                    
                    current = current.plusMinutes(30);
                }
            }

            logger.info("Slot seeding done. created={}, updated={}", totalSlotsCreated, totalSlotsUpdated);
            // Backward-compat safety pass for partially seeded data
            fillMissingLastSlots(slotRepository, today, endDate);
        };
    }

    /**
     * Fill missing last slots (16:30-17:00) for all dates if they don't exist.
     * This is a safety check for databases that were created before the fix.
     * Also check for any other completely missing time slots.
     */
    private void fillMissingLastSlots(SlotRepository slotRepository, LocalDate fromDate, LocalDate toDate) {
        logger.info("Checking and filling missing slots (all incomplete dates)...");
        
        LocalDate current = fromDate;
        int slotsAdded = 0;
        
        while (!current.isAfter(toDate)) {
            if (current.getDayOfWeek().getValue() != 7) {  // Skip Sunday
                LocalDateTime dayStart = LocalDateTime.of(current, CLINIC_OPEN_TIME);
                LocalDateTime dayEnd = LocalDateTime.of(current, CLINIC_CLOSE_TIME);
                
                // Count existing slots for this day
                List<Slot> existingDaySlots = slotRepository.findAllSlotsBetweenTimes(dayStart, dayEnd);
                
                // If day doesn't have full 18 slots, fill missing ones
                if (existingDaySlots.size() < 18) {
                    logger.warn("Day {} only has {} slots, expected 18. Filling missing slots...", current, existingDaySlots.size());
                    
                    // Create all missing slots for this day
                    LocalDateTime slotTime = dayStart;
                    while (slotTime.isBefore(dayEnd) || slotTime.equals(dayEnd.minusMinutes(30))) {
                        // Check if this slot exists
                        java.util.Optional<Slot> existingSlot = slotRepository.findBySlotTime(slotTime);
                        
                        if (existingSlot.isEmpty()) {
                            // Create missing slot
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
                            logger.info("Re-activated existing slot: {}", slotTime);
                        }
                        
                        slotTime = slotTime.plusMinutes(30);
                    }
                }
            }
            
            current = current.plusDays(1);
        }
        
        if (slotsAdded > 0) {
            logger.info("Added {} missing slots total", slotsAdded);
        } else {
            logger.info("No missing slots found, database is complete");
        }
    }
}
