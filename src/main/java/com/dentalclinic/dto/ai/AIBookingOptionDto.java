package com.dentalclinic.dto.ai;

public class AIBookingOptionDto {

    private String date;
    private String startTime;
    private String endTime;
    private String displayText;

    public AIBookingOptionDto() {
    }

    public AIBookingOptionDto(String date, String startTime, String endTime, String displayText) {
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.displayText = displayText;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getDisplayText() {
        return displayText;
    }

    public void setDisplayText(String displayText) {
        this.displayText = displayText;
    }
}