package com.dentalclinic.dto.ai;

import java.util.ArrayList;
import java.util.List;

public class AIBookingSuggestionResponse {

    private String intent;
    private String originalMessage;
    private String normalizedMessage;
    private String preferredDate;
    private String timePreference;
    private String urgency;
    private String assistantMessage;
    private String preferredTime;

    private List<AIServiceSuggestionDto> services = new ArrayList<>();
    private List<AIBookingOptionDto> slotOptions = new ArrayList<>();

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getOriginalMessage() {
        return originalMessage;
    }

    public void setOriginalMessage(String originalMessage) {
        this.originalMessage = originalMessage;
    }

    public String getNormalizedMessage() {
        return normalizedMessage;
    }

    public void setNormalizedMessage(String normalizedMessage) {
        this.normalizedMessage = normalizedMessage;
    }

    public String getPreferredDate() {
        return preferredDate;
    }

    public void setPreferredDate(String preferredDate) {
        this.preferredDate = preferredDate;
    }

    public String getTimePreference() {
        return timePreference;
    }

    public void setTimePreference(String timePreference) {
        this.timePreference = timePreference;
    }

    public String getUrgency() {
        return urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public String getAssistantMessage() {
        return assistantMessage;
    }

    public void setAssistantMessage(String assistantMessage) {
        this.assistantMessage = assistantMessage;
    }

    public List<AIServiceSuggestionDto> getServices() {
        return services;
    }

    public void setServices(List<AIServiceSuggestionDto> services) {
        this.services = services;
    }

    public List<AIBookingOptionDto> getSlotOptions() {
        return slotOptions;
    }

    public void setSlotOptions(List<AIBookingOptionDto> slotOptions) {
        this.slotOptions = slotOptions;
    }

    public String getPreferredTime() {return preferredTime;}

    public void setPreferredTime(String preferredTime) {this.preferredTime = preferredTime;}
}