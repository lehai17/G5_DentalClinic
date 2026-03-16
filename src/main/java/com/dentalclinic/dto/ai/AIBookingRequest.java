package com.dentalclinic.dto.ai;

import jakarta.validation.constraints.NotBlank;

public class AIBookingRequest {

    @NotBlank(message = "message is required")
    private String message;

    private String contactChannel;
    private String contactValue;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getContactChannel() {
        return contactChannel;
    }

    public void setContactChannel(String contactChannel) {
        this.contactChannel = contactChannel;
    }

    public String getContactValue() {
        return contactValue;
    }

    public void setContactValue(String contactValue) {
        this.contactValue = contactValue;
    }
}