package com.dentalclinic.dto.ai;

import java.util.ArrayList;
import java.util.List;

public class LLMBookingInterpretation {

    private String intent;
    private List<String> serviceKeywords = new ArrayList<>();
    private String preferredDate;
    private String timePreference;
    private String urgency;
    private String normalizedMessage;
    private String preferredTime;

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public List<String> getServiceKeywords() {
        return serviceKeywords;
    }

    public void setServiceKeywords(List<String> serviceKeywords) {
        this.serviceKeywords = serviceKeywords;
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

    public String getNormalizedMessage() {
        return normalizedMessage;
    }

    public void setNormalizedMessage(String normalizedMessage) {
        this.normalizedMessage = normalizedMessage;
    }

    public String getPreferredTime() {return preferredTime;}

    public void setPreferredTime(String preferredTime) {this.preferredTime = preferredTime;}
}