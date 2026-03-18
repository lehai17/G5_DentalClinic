package com.dentalclinic.service.ai;

import com.dentalclinic.dto.ai.LLMBookingInterpretation;

public interface LLMService {
    LLMBookingInterpretation interpretBookingRequest(String userMessage);
}