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
            
            LocalDateTime dayStart = LocalDateTime.of(today, CLINIC_OPEN_TIME);
            LocalDateTime dayEnd = LocalDateTime.of(today, CLINIC_CLOSE_TIME);
            
            List<Slot> existingSlots = slotRepository.findActiveSlotsForDateRange(dayStart, dayEnd);
            
            if (!existingSlots.isEmpty()) {
                logger.info("Slots already exist for today, skipping seed");
                // But still check and fill missing last slots
                fillMissingLastSlots(slotRepository, today, endDate);
                return;
            }
            
            logger.info("Seeding slots for next {} days with capacity {}", DAYS_TO_SEED, DEFAULT_CAPACITY);
            
            int totalSlotsCreated = 0;
            
            for (int day = 0; day < DAYS_TO_SEED; day++) {
                LocalDate date = today.plusDays(day);
                
                if (date.getDayOfWeek().getValue() == 7) {
                    continue;
                }
                
                LocalDateTime current = LocalDateTime.of(date, CLINIC_OPEN_TIME);
                LocalDateTime end = LocalDateTime.of(date, CLINIC_CLOSE_TIME);
                
                // FIX: Include the last slot (16:30-17:00) by using <= instead of <
                while (current.isBefore(end) || current.equals(end.minusMinutes(30))) {
                    Slot slot = new Slot(current, DEFAULT_CAPACITY);
                    slotRepository.save(slot);
                    totalSlotsCreated++;
                    
                    current = current.plusMinutes(30);
                }
            }
            
            logger.info("Successfully seeded {} slots", totalSlotsCreated);
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
                
                    // Count existing VALID slots for this day (08:00-17:00 range)
                    List<Slot> existingDaySlots = slotRepository.findAllSlotsBetweenTimes(dayStart, dayEnd);
                
                    // If day doesn't have full 18 slots, fill missing ones
                    if (existingDaySlots.size() < 18) {
                        logger.warn("Day {} only has {} slots, expected 18. Filling missing slots...", current, existingDaySlots.size());
                    
                        // Create all missing slots for this day
                        LocalDateTime slotTime = dayStart;
                        while (slotTime.isBefore(dayEnd) || slotTime.equals(dayEnd.minusMinutes(30))) {
                            // Check if this slot exists
                            java.util.Optional<Slot> existingSlot = slotRepository.findBySlotTimeAndActiveTrue(slotTime);
                        
                            if (existingSlot.isEmpty()) {
                                // Create missing slot with proper defaults
                                Slot slot = new Slot(slotTime, DEFAULT_CAPACITY);
                                slot.setActive(true);
                                slot.setBookedCount(0);
                                slotRepository.save(slot);
                                slotsAdded++;
                                logger.info("Created missing slot: {}", slotTime);
                            } else {
                                // Ensure existing slot is properly configured
                                Slot existing = existingSlot.get();
                                boolean changed = false;
                                if (!existing.isActive() || existing.getCapacity() <= 0) {
                                    existing.setActive(true);
                                    if (existing.getCapacity() <= 0) {
                                        existing.setCapacity(DEFAULT_CAPACITY);
                                    }
                                    changed = true;
                                }
                                // bookedCount must be within [0, capacity]
                                if (existing.getBookedCount() < 0 || existing.getBookedCount() > existing.getCapacity()) {
                                    existing.setBookedCount(0);
                                    changed = true;
                                }
                                if (changed) {
                                    slotRepository.save(existing);
                                    logger.info("Fixed existing slot: {} (repaired properties)", slotTime);
                                }
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
