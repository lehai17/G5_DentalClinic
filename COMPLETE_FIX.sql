-- ============================================================================
-- DENTAL CLINIC SLOT SYSTEM - COMPLETE FIX & REGENERATION SCRIPT
-- ============================================================================
-- EXECUTION INSTRUCTIONS:
-- 1. Open SQL Server Management Studio
-- 2. Connect to DentalClinic database
-- 3. Execute this entire script
-- 4. Restart your Spring Boot application
-- 5. Application will auto-regenerate slots with SlotSeeder
-- ============================================================================

PRINT '======================================'
PRINT 'DENTAL CLINIC SLOT SYSTEM FIX'
PRINT '======================================'

-- STEP 1: DISPLAY CURRENT STATE
PRINT ''
PRINT 'STEP 1: CURRENT DATABASE STATE'
PRINT '================================'

SELECT COUNT(*) as TotalSlotsBeforeFix FROM [dbo].[slot];

PRINT ''
PRINT 'Slot time distribution (first 20 rows):'
SELECT TOP 20
    id,
    CONVERT(DATE, slot_time) as SlotDate,
    CAST(slot_time AS TIME) as SlotTime,
    booked_count,
    capacity,
    is_active
FROM [dbo].[slot]
ORDER BY slot_time;

-- STEP 2: IDENTIFY PROBLEMATIC SLOTS
PRINT ''
PRINT 'STEP 2: IDENTIFYING INVALID SLOTS'
PRINT '================================='

DECLARE @InvalidCount INT
SELECT @InvalidCount = COUNT(*) FROM [dbo].[slot]
WHERE CAST(slot_time AS TIME) < '08:00:00' 
   OR CAST(slot_time AS TIME) > '16:30:00'
   OR is_active = 0

PRINT CAST(@InvalidCount AS VARCHAR(10)) + ' invalid slots found (times outside 08:00-16:30 or inactive)'

-- STEP 3: DELETE INVALID SLOTS
PRINT ''
PRINT 'STEP 3: DELETING INVALID SLOTS'
PRINT '============================='

DELETE FROM [dbo].[appointment_slot]
WHERE slot_id IN (
    SELECT id FROM [dbo].[slot]
    WHERE CAST(slot_time AS TIME) < '08:00:00' 
       OR CAST(slot_time AS TIME) > '16:30:00'
       OR is_active = 0
)

DELETE FROM [dbo].[slot]
WHERE CAST(slot_time AS TIME) < '08:00:00' 
   OR CAST(slot_time AS TIME) > '16:30:00'
   OR is_active = 0

PRINT 'Invalid slots deleted'

-- STEP 4: FIX BOOKED COUNTS
PRINT ''
PRINT 'STEP 4: FIXING INVALID BOOKED COUNTS'
PRINT '===================================='

-- Fix slots where booked_count > capacity
UPDATE [dbo].[slot]
SET booked_count = 0
WHERE booked_count > capacity

PRINT 'Booked counts reset for slots exceeding capacity'

-- STEP 5: ENSURE ALL SLOTS HAVE CORRECT PROPERTIES
PRINT ''
PRINT 'STEP 5: NORMALIZING SLOT PROPERTIES'
PRINT '===================================='

UPDATE [dbo].[slot]
SET is_active = 1,
    capacity = CASE WHEN capacity <= 0 THEN 3 ELSE capacity END,
    booked_count = CASE WHEN booked_count < 0 THEN 0 ELSE booked_count END
WHERE 1=1

PRINT 'All slots normalized'

-- STEP 6: DISPLAY CURRENT STATE AFTER CLEANUP
PRINT ''
PRINT 'STEP 6: STATE AFTER CLEANUP'
PRINT '==========================='

SELECT COUNT(*) as TotalSlotsAfterFix FROM [dbo].[slot]

-- STEP 7: ANALYZE SLOT DISTRIBUTION
PRINT ''
PRINT 'STEP 7: SLOT DISTRIBUTION ANALYSIS'
PRINT '=================================='

SELECT 
    CONVERT(DATE, slot_time) as SlotDate,
    COUNT(*) as SlotsCount,
    SUM(CASE WHEN booked_count < capacity THEN 1 ELSE 0 END) as AvailableSlots,
    SUM(CASE WHEN booked_count >= capacity THEN 1 ELSE 0 END) as FullSlots,
    SUM(booked_count) as TotalBooked,
    SUM(capacity) as TotalCapacity
FROM [dbo].[slot]
GROUP BY CONVERT(DATE, slot_time)
ORDER BY CONVERT(DATE, slot_time)

-- STEP 8: FIND DATES WITH INCOMPLETE SLOT SETS
PRINT ''
PRINT 'STEP 8: INCOMPLETE DATES (Missing slots)'
PRINT '======================================='

DECLARE @IncompleteDates TABLE (SlotDate DATE, SlotCount INT)

INSERT INTO @IncompleteDates
SELECT 
    CONVERT(DATE, slot_time) as SlotDate,
    COUNT(*) as SlotCount
FROM [dbo].[slot]
GROUP BY CONVERT(DATE, slot_time)
HAVING COUNT(*) < 18

IF EXISTS (SELECT 1 FROM @IncompleteDates)
BEGIN
    PRINT 'WARNING: The following dates have fewer than 18 slots:'
    SELECT * FROM @IncompleteDates
    PRINT ''
    PRINT 'These will be auto-filled when Spring Boot application starts'
END
ELSE
BEGIN
    PRINT 'All dates have complete slot sets (18 slots each)'
END

-- STEP 9: DISPLAY SAMPLE VALID SLOTS
PRINT ''
PRINT 'STEP 9: SAMPLE OF VALID SLOTS'
PRINT '============================='

SELECT TOP 36
    CONVERT(DATE, slot_time) as SlotDate,
    CAST(slot_time AS TIME) as SlotTime,
    booked_count as 'Booked/Capacity',
    CASE WHEN booked_count < capacity THEN 'AVAILABLE' ELSE 'FULL' END as Status
FROM [dbo].[slot]
ORDER BY slot_time

-- STEP 10: FINAL SUMMARY
PRINT ''
PRINT 'STEP 10: FINAL SUMMARY'
PRINT '===================='

DECLARE @TotalSlots INT
DECLARE @DaysWithSlots INT
DECLARE @AvailableSlots INT
DECLARE @FullSlots INT
DECLARE @ValidDates INT

SELECT 
    @TotalSlots = COUNT(*),
    @DaysWithSlots = COUNT(DISTINCT CONVERT(DATE, slot_time)),
    @AvailableSlots = SUM(CASE WHEN booked_count < capacity THEN 1 ELSE 0 END),
    @FullSlots = SUM(CASE WHEN booked_count >= capacity THEN 1 ELSE 0 END)
FROM [dbo].[slot]

SELECT 
    @TotalSlots as TotalSlots,
    @DaysWithSlots as DaysWithSlots,
    @AvailableSlots as AvailableSlots,
    @FullSlots as FullSlots,
    (CASE WHEN @FullSlots > 0 THEN 'WARNING: Some slots are marked as FULL' 
           ELSE 'OK: All available slots have capacity' END) as Status

-- STEP 11: IMPORTANT NEXT STEPS
PRINT ''
PRINT 'IMPORTANT: NEXT STEPS'
PRINT '==================='
PRINT ''
PRINT '1. Rebuild your project:'
PRINT '   mvn clean package'
PRINT ''
PRINT '2. Restart Spring Boot application'
PRINT '   The application will auto-generate missing slots via SlotSeeder'
PRINT ''
PRINT '3. Check application logs for:'
PRINT '   - "Seeding slots for next 30 days"'
PRINT '   - "Successfully seeded X slots"'
PRINT '   - "Added X missing slots total"'
PRINT ''
PRINT '4. Test in web browser:'
PRINT '   - Navigate to booking page'
PRINT '   - Select a future date'
PRINT '   - Should see 18 time slots from 08:00-16:30'
PRINT '   - All should be available for booking'
PRINT ''

PRINT ''
PRINT '======================================'
PRINT 'DATABASE FIX COMPLETED SUCCESSFULLY'
PRINT '======================================'
