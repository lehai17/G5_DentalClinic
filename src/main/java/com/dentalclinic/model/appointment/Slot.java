package com.dentalclinic.model.appointment;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "slot")
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "slot_time", nullable = false)
    private LocalDateTime slotTime;

    @Column(name = "capacity", nullable = false)
    private int capacity = 3;

    @Column(name = "booked_count", nullable = false)
    private int bookedCount = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Slot() {
    }

    public Slot(LocalDateTime slotTime, int capacity) {
        this.slotTime = slotTime;
        this.capacity = capacity;
        this.bookedCount = 0;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDateTime getSlotTime() {
        return slotTime;
    }

    public void setSlotTime(LocalDateTime slotTime) {
        this.slotTime = slotTime;
    }

    public LocalTime getStartTime() {
        return slotTime != null ? slotTime.toLocalTime() : null;
    }

    public LocalTime getEndTime() {
        return slotTime != null ? slotTime.toLocalTime().plusMinutes(30) : null;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getBookedCount() {
        return bookedCount;
    }

    public void setBookedCount(int bookedCount) {
        this.bookedCount = bookedCount;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAvailable() {
        return active && bookedCount < capacity;
    }

    public int getAvailableSpots() {
        return capacity - bookedCount;
    }
}
