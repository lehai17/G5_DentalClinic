package com.dentalclinic.dto.admin;

import java.time.LocalDate;

public class SlotDayBadgeDto {
    private LocalDate date;
    private int booked;
    private int capacity;

    private String densityStatus; // GREEN, YELLOW, RED
    private boolean active = true; // NEW FIELD

    public SlotDayBadgeDto() {
    }

    public SlotDayBadgeDto(LocalDate date, int booked, int capacity) {
        this.date = date;
        this.booked = booked;
        this.capacity = capacity;
        calculateDensity();
    }

    public void calculateDensity() {
        if (capacity <= 0) {
            this.densityStatus = "GREEN"; // Default to GREEN if no capacity yet
            return;
        }
        double ratio = (double) booked / capacity;
        if (ratio < 0.7) {
            this.densityStatus = "GREEN";
        } else if (ratio < 1.0) {
            this.densityStatus = "YELLOW";
        } else {
            this.densityStatus = "RED";
        }
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getDensityStatus() {
        return densityStatus;
    }

    public void setDensityStatus(String densityStatus) {
        this.densityStatus = densityStatus;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getBooked() {
        return booked;
    }

    public void setBooked(int booked) {
        this.booked = booked;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }
}
