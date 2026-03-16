package com.dentalclinic.service.ai;

import com.dentalclinic.dto.ai.AIBookingRequest;
import com.dentalclinic.dto.ai.AIBookingSuggestionResponse;

public interface AIBookingService {
    AIBookingSuggestionResponse suggest(Long userId, AIBookingRequest request);
}