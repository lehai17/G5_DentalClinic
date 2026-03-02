package com.dentalclinic.repository;

import com.dentalclinic.model.appointment.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Long> {

    List<Slot> findBySlotTimeBetweenAndActiveTrue(LocalDateTime start, LocalDateTime end);

    List<Slot> findBySlotTimeBetweenAndActiveTrueOrderBySlotTimeAsc(LocalDateTime start, LocalDateTime end);

    Optional<Slot> findBySlotTimeAndActiveTrue(LocalDateTime slotTime);

    Optional<Slot> findBySlotTime(LocalDateTime slotTime);

    @Query("SELECT s FROM Slot s WHERE s.slotTime >= :startDate AND s.slotTime < :endDate AND s.active = true ORDER BY s.slotTime ASC")
    List<Slot> findActiveSlotsForDateRange(@Param("startDate") LocalDateTime startDate, 
                                            @Param("endDate") LocalDateTime endDate);

    @Query("SELECT s FROM Slot s WHERE FUNCTION('DATE', s.slotTime) = :date AND s.active = true ORDER BY s.slotTime ASC")
    List<Slot> findActiveSlotsForDate(@Param("date") LocalDate date);

    @Modifying
    @Query("UPDATE Slot s SET s.bookedCount = s.bookedCount + 1 WHERE s.slotTime = :slotTime AND s.bookedCount < s.capacity AND s.active = true")
    int incrementBookedCountIfAvailable(@Param("slotTime") LocalDateTime slotTime);

    @Modifying
    @Query("UPDATE Slot s SET s.bookedCount = s.bookedCount - 1 WHERE s.slotTime = :slotTime AND s.bookedCount > 0 AND s.active = true")
    int decrementBookedCount(@Param("slotTime") LocalDateTime slotTime);

    @Query("SELECT s FROM Slot s WHERE s.slotTime = :slotTime AND s.active = true")
    Optional<Slot> findBySlotTimeForUpdate(@Param("slotTime") LocalDateTime slotTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.slotTime = :slotTime AND s.active = true")
    Optional<Slot> findBySlotTimeAndActiveTrueForUpdate(@Param("slotTime") LocalDateTime slotTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Slot s WHERE s.slotTime >= :fromTime AND s.slotTime < :toTime AND s.active = true ORDER BY s.slotTime ASC")
    List<Slot> findActiveSlotsForUpdate(@Param("fromTime") LocalDateTime fromTime,
                                        @Param("toTime") LocalDateTime toTime);

    @Query("SELECT CASE WHEN COUNT(s) > 0 THEN true ELSE false END FROM Slot s WHERE s.slotTime = :slotTime AND s.active = true AND s.bookedCount < s.capacity")
    boolean hasAvailableCapacity(@Param("slotTime") LocalDateTime slotTime);

    @Query("SELECT s FROM Slot s WHERE s.slotTime BETWEEN :startTime AND :endTime AND s.active = true ORDER BY s.slotTime ASC")
    List<Slot> findSlotsBetweenTimes(@Param("startTime") LocalDateTime startTime, 
                                       @Param("endTime") LocalDateTime endTime);

    @Query("SELECT s FROM Slot s WHERE s.slotTime >= :fromTime AND s.slotTime < :toTime AND s.active = true AND s.bookedCount < s.capacity ORDER BY s.slotTime ASC")
    List<Slot> findAvailableSlotsBetweenTimes(@Param("fromTime") LocalDateTime fromTime, 
                                               @Param("toTime") LocalDateTime toTime);

    @Query("SELECT s FROM Slot s WHERE s.slotTime >= :fromTime AND s.slotTime < :toTime AND s.active = true ORDER BY s.slotTime ASC")
    List<Slot> findAllSlotsBetweenTimes(@Param("fromTime") LocalDateTime fromTime, 
                                         @Param("toTime") LocalDateTime toTime);

    /**
     * Get available slots for a service (only future slots > now).
     * Used in customer booking to get slots with enough consecutive capacity.
     */
    @Query("SELECT s FROM Slot s WHERE s.slotTime > :currentTime AND s.slotTime >= :fromTime AND s.slotTime < :toTime AND s.active = true AND s.bookedCount < s.capacity ORDER BY s.slotTime ASC")
    List<Slot> findAvailableSlotsForService(@Param("currentTime") LocalDateTime currentTime,
                                             @Param("fromTime") LocalDateTime fromTime, 
                                             @Param("toTime") LocalDateTime toTime);

    /**
     * Get all slots for a date, only future slots (> now).
     * Used in customer booking to list all time slots.
     */
    @Query("SELECT s FROM Slot s WHERE s.slotTime > :currentTime AND s.slotTime >= :fromTime AND s.slotTime < :toTime AND s.active = true ORDER BY s.slotTime ASC")
    List<Slot> findAllFutureSlotsForDate(@Param("currentTime") LocalDateTime currentTime,
                                          @Param("fromTime") LocalDateTime fromTime, 
                                          @Param("toTime") LocalDateTime toTime);

    /**
     * Get available slots within a time range for TODAY ONLY (no > currentTime filter).
     * This returns slots from current time onwards without filtering based on exact currentTime.
     * Used when selectedDate == LocalDate.now() to ensure current slot and future slots are included.
     */
    @Query("SELECT s FROM Slot s WHERE s.slotTime >= :fromTime AND s.slotTime < :toTime AND s.active = true AND s.bookedCount < s.capacity ORDER BY s.slotTime ASC")
    List<Slot> findAvailableSlotsForToday(@Param("fromTime") LocalDateTime fromTime,
                                           @Param("toTime") LocalDateTime toTime);

    /**
     * Get all slots within a time range for TODAY ONLY (no > currentTime filter).
     * This returns slots from current time onwards without filtering based on exact currentTime.
     * Used when selectedDate == LocalDate.now() to show all slots with time >= current slot time.
     */
    @Query("SELECT s FROM Slot s WHERE s.slotTime >= :fromTime AND s.slotTime < :toTime AND s.active = true ORDER BY s.slotTime ASC")
    List<Slot> findAllSlotsForToday(@Param("fromTime") LocalDateTime fromTime,
                                     @Param("toTime") LocalDateTime toTime);

    @Modifying
    @Transactional
    @Query("UPDATE Slot s SET s.active = false " +
            "WHERE s.slotTime >= :start AND s.slotTime <= :end")
    void disableSlotsInPeriod(@Param("start") LocalDateTime start,
                              @Param("end") LocalDateTime end);
}
