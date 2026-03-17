package com.dentalclinic.service.ai;

import com.dentalclinic.dto.ai.*;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalTime;
import java.util.Comparator;

@Service
public class AIBookingServiceImpl implements AIBookingService {

    private final LLMService llmService;
    private final ServiceMatcher serviceMatcher;
    private final CustomerAppointmentService customerAppointmentService;

    @Value("${ai.booking.max-suggestions:5}")
    private int maxSuggestions;

    public AIBookingServiceImpl(LLMService llmService,
                                ServiceMatcher serviceMatcher,
                                CustomerAppointmentService customerAppointmentService) {
        this.llmService = llmService;
        this.serviceMatcher = serviceMatcher;
        this.customerAppointmentService = customerAppointmentService;
    }

    @Override
    public AIBookingSuggestionResponse suggest(Long userId, AIBookingRequest request) {
        LLMBookingInterpretation interpretation = llmService.interpretBookingRequest(request.getMessage());

        System.out.println("=========== AI BOOKING DEBUG ===========");
        System.out.println("User message: " + request.getMessage());
        System.out.println("Interpret intent: " + interpretation.getIntent());
        System.out.println("Interpret keywords: " + interpretation.getServiceKeywords());
        System.out.println("Interpret preferredDate: " + interpretation.getPreferredDate());
        System.out.println("Interpret timePreference: " + interpretation.getTimePreference());

        List<Services> matchedServices = serviceMatcher.matchServices(interpretation.getServiceKeywords());
        System.out.println("Matched service count: " + matchedServices.size());
        for (Services s : matchedServices) {
            System.out.println("Matched service: " + s.getId() + " - " + s.getName());
        }

        List<Long> serviceIds = matchedServices.stream()
                .map(Services::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        LocalDate preferredDate = resolvePreferredDate(interpretation.getPreferredDate());
        List<SlotDto> slots = customerAppointmentService.getAvailableSlots(userId, serviceIds, preferredDate);

        List<SlotDto> filteredSlots = filterByTimePreference(slots, interpretation.getTimePreference());
        if (filteredSlots.isEmpty()) {
            filteredSlots = slots;
        }

// Chỉ giữ các slot thực sự chọn được
        filteredSlots = keepSelectableSlots(filteredSlots);

        String preferredTime = interpretation.getPreferredTime();
        SlotDto exactRequestedSlot = findExactRequestedSlot(filteredSlots, preferredTime);

// Chỉ true khi slot tồn tại và thật sự chọn được
        boolean requestedSlotAvailable = exactRequestedSlot != null && isSelectableSlot(exactRequestedSlot);

        filteredSlots = prioritizeAndExcludeRequestedTime(
                filteredSlots,
                preferredTime,
                !requestedSlotAvailable
        );

        String requestedTimeMessage = null;
        if (preferredTime != null && !preferredTime.isBlank() && !requestedSlotAvailable) {
            requestedTimeMessage = "Khung giờ " + preferredTime
                    + " hiện đã có người đặt. Tôi gợi ý các khung giờ gần nhất khác cho bạn.";
        }

        System.out.println("Slot count before filter: " + slots.size());
        System.out.println("Slot count after filter: " + filteredSlots.size());
        System.out.println("=======================================");

        AIBookingSuggestionResponse response = new AIBookingSuggestionResponse();
        response.setIntent(interpretation.getIntent());
        response.setOriginalMessage(request.getMessage());
        response.setNormalizedMessage(interpretation.getNormalizedMessage());
        response.setPreferredDate(preferredDate.toString());
        response.setPreferredTime(interpretation.getPreferredTime());
        response.setTimePreference(interpretation.getTimePreference());
        response.setUrgency(interpretation.getUrgency());

        response.setServices(
                matchedServices.stream()
                        .map(s -> new AIServiceSuggestionDto(
                                s.getId(),
                                s.getName(),
                                s.getDurationMinutes(),
                                s.getPrice()
                        ))
                        .collect(Collectors.toList())
        );

        response.setSlotOptions(
                filteredSlots.stream()
                        .limit(maxSuggestions)
                        .map(slot -> new AIBookingOptionDto(
                                preferredDate.toString(),
                                slot.getStartTime() == null ? "" : slot.getStartTime().toString(),
                                slot.getEndTime() == null ? "" : slot.getEndTime().toString(),
                                buildDisplayText(preferredDate, slot)
                        ))
                        .collect(Collectors.toList())
        );

        if (requestedTimeMessage != null) {
            response.setAssistantMessage(requestedTimeMessage);
        } else {
            response.setAssistantMessage(buildAssistantMessage(response));
        }
        return response;
    }

    private LocalDate resolvePreferredDate(String preferredDate) {
        try {
            if (preferredDate != null && !preferredDate.isBlank()) {
                LocalDate parsed = LocalDate.parse(preferredDate);
                if (!parsed.isBefore(LocalDate.now())) {
                    return parsed;
                }
            }
        } catch (Exception ignored) {
        }
        return LocalDate.now().plusDays(1);
    }

    private List<SlotDto> filterByTimePreference(List<SlotDto> slots, String timePreference) {
        if (slots == null || slots.isEmpty()) return List.of();
        if (timePreference == null || timePreference.isBlank() || "any".equalsIgnoreCase(timePreference)) {
            return slots;
        }

        return slots.stream()
                .filter(slot -> {
                    if (slot.getStartTime() == null) return false;
                    int hour = slot.getStartTime().getHour();

                    return switch (timePreference.toLowerCase(Locale.ROOT)) {
                        case "morning" -> hour < 12;
                        case "afternoon" -> hour >= 12 && hour < 17;
                        case "evening" -> hour >= 17;
                        default -> true;
                    };
                })
                .collect(Collectors.toList());
    }

    private String buildDisplayText(LocalDate date, SlotDto slot) {
        String start = slot.getStartTime() == null ? "" : slot.getStartTime().toString();
        String end = slot.getEndTime() == null ? "" : slot.getEndTime().toString();
        return date + " | " + start + " - " + end;
    }

    private String buildAssistantMessage(AIBookingSuggestionResponse response) {
        if (response.getServices().isEmpty()) {
            return "Tôi chưa xác định rõ dịch vụ phù hợp. Bạn có thể mô tả chi tiết hơn nhu cầu khám.";
        }

        if (response.getSlotOptions().isEmpty()) {
            return "Tôi đã xác định được dịch vụ phù hợp nhưng ngày này chưa có khung giờ trống. Bạn hãy thử ngày khác hoặc khung giờ khác.";
        }

        String serviceNames = response.getServices().stream()
                .map(AIServiceSuggestionDto::getName)
                .collect(Collectors.joining(", "));

        if (response.getPreferredTime() != null && !response.getPreferredTime().isBlank()) {
            return "Tôi gợi ý dịch vụ: " + serviceNames
                    + ". Dưới đây là các khung giờ gần thời điểm " + response.getPreferredTime() + " còn trống để bạn chọn.";
        }

        return "Tôi gợi ý dịch vụ: " + serviceNames
                + ". Dưới đây là các khung giờ phù hợp để bạn chọn nhanh.";
    }

    private List<SlotDto> prioritizeByPreferredTime(List<SlotDto> slots, String preferredTime) {
        if (slots == null || slots.isEmpty()) return List.of();
        if (preferredTime == null || preferredTime.isBlank()) return slots;

        try {
            LocalTime target = LocalTime.parse(preferredTime);

            return slots.stream()
                    .sorted(Comparator.comparingInt(slot -> {
                        if (slot.getStartTime() == null) return Integer.MAX_VALUE;
                        return Math.abs(slot.getStartTime().toSecondOfDay() - target.toSecondOfDay());
                    }))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return slots;
        }
    }
    private SlotDto findExactRequestedSlot(List<SlotDto> slots, String preferredTime) {
        if (slots == null || slots.isEmpty()) return null;
        if (preferredTime == null || preferredTime.isBlank()) return null;

        try {
            LocalTime target = LocalTime.parse(preferredTime);

            return slots.stream()
                    .filter(this::isSelectableSlot)
                    .filter(slot -> slot.getStartTime() != null)
                    .filter(slot -> slot.getStartTime().equals(target))
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private List<SlotDto> prioritizeAndExcludeRequestedTime(List<SlotDto> slots,
                                                            String preferredTime,
                                                            boolean excludeExactMatch) {
        if (slots == null || slots.isEmpty()) return List.of();
        if (preferredTime == null || preferredTime.isBlank()) return slots;

        try {
            LocalTime target = LocalTime.parse(preferredTime);

            return slots.stream()
                    .filter(slot -> {
                        if (!excludeExactMatch) return true;
                        return slot.getStartTime() == null || !slot.getStartTime().equals(target);
                    })
                    .sorted(Comparator.comparingInt(slot -> {
                        if (slot.getStartTime() == null) return Integer.MAX_VALUE;
                        return Math.abs(slot.getStartTime().toSecondOfDay() - target.toSecondOfDay());
                    }))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return slots;
        }
    }

    private boolean isSelectableSlot(SlotDto slot) {
        if (slot == null) return false;
        if (slot.isDisabled()) return false;

        if (slot.getAvailableSpots() > 0) {
            return true;
        }

        return slot.isAvailable();
    }

    private List<SlotDto> keepSelectableSlots(List<SlotDto> slots) {
        if (slots == null || slots.isEmpty()) return List.of();
        return slots.stream()
                .filter(this::isSelectableSlot)
                .collect(Collectors.toList());
    }
}
