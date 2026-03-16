package com.dentalclinic.dto.admin;

import java.time.LocalDate;

public class CalendarCellDto {
    private LocalDate date;
    private boolean inMonth;

    public CalendarCellDto() {
    }

    public CalendarCellDto(LocalDate date, boolean inMonth) {
        this.date = date;
        this.inMonth = inMonth;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public boolean isInMonth() {
        return inMonth;
    }

    public void setInMonth(boolean inMonth) {
        this.inMonth = inMonth;
    }
}

