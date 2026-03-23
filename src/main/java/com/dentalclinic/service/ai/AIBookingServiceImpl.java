package com.dentalclinic.service.ai;

import com.dentalclinic.dto.ai.AIBookingOptionDto;
import com.dentalclinic.dto.ai.AIBookingRequest;
import com.dentalclinic.dto.ai.AIBookingSuggestionResponse;
import com.dentalclinic.dto.ai.AIServiceSuggestionDto;
import com.dentalclinic.dto.ai.LLMBookingInterpretation;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

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

        List<Services> matchedServices = serviceMatcher.matchServices(
                interpretation.getServiceKeywords(),
                request.getMessage()
        );

        Services primaryService = matchedServices.isEmpty() ? null : matchedServices.get(0);

        List<Long> primaryServiceIds = primaryService == null || primaryService.getId() == null
                ? List.of()
                : List.of(primaryService.getId());

        LocalDate preferredDate = resolvePreferredDate(interpretation.getPreferredDate());
        List<SlotDto> slots = customerAppointmentService.getAvailableSlots(userId, primaryServiceIds, preferredDate);

        List<SlotDto> filteredSlots = filterByTimePreference(slots, interpretation.getTimePreference());
        if (filteredSlots.isEmpty()) {
            filteredSlots = slots;
        }

        filteredSlots = keepSelectableSlots(filteredSlots);

        String preferredTime = interpretation.getPreferredTime();
        SlotDto exactRequestedSlot = findExactRequestedSlot(filteredSlots, preferredTime);
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

        int primaryDurationMinutes = getPrimaryDurationMinutes(primaryService);

        response.setSlotOptions(
                filteredSlots.stream()
                        .limit(maxSuggestions)
                        .map(slot -> new AIBookingOptionDto(
                                slot.getId(),
                                preferredDate.toString(),
                                slot.getStartTime() == null ? "" : slot.getStartTime().toString(),
                                calculateSuggestedEndTime(slot, primaryDurationMinutes),
                                buildDisplayText(preferredDate, slot, primaryDurationMinutes)
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

    private String buildAssistantMessage(AIBookingSuggestionResponse response) {
        if (response.getServices().isEmpty()) {
            return "Tôi chưa xác định rõ dịch vụ phù hợp. Bạn có thể mô tả chi tiết hơn nhu cầu khám.";
        }

        if (response.getSlotOptions().isEmpty()) {
            return "Tôi đã xác định được dịch vụ phù hợp nhưng ngày này chưa có khung giờ trống. Bạn hãy thử ngày khác hoặc khung giờ khác.";
        }

        boolean hasMetal = response.getServices().stream()
                .anyMatch(s -> s.getName() != null && containsAnyText(normalizeText(s.getName()),
                        "kim loai", "mac cai", "mac cai thuong", "nieng rang thuong", "thuong"));

        boolean hasInvis = response.getServices().stream()
                .anyMatch(s -> s.getName() != null && containsAnyText(normalizeText(s.getName()),
                        "invisalign", "trong suot", "khay trong", "khay trong suot"));

        if (hasMetal && hasInvis) {
            AIServiceSuggestionDto first = response.getServices().get(0);
            boolean invisFirst = first.getName() != null && containsAnyText(normalizeText(first.getName()),
                    "invisalign", "trong suot", "khay trong", "khay trong suot");

            AIServiceSuggestionDto metal = response.getServices().stream()
                    .filter(s -> s.getName() != null && containsAnyText(normalizeText(s.getName()),
                            "kim loai", "mac cai", "mac cai thuong", "nieng rang thuong", "thuong"))
                    .findFirst()
                    .orElse(null);

            AIServiceSuggestionDto invis = response.getServices().stream()
                    .filter(s -> s.getName() != null && containsAnyText(normalizeText(s.getName()),
                            "invisalign", "trong suot", "khay trong", "khay trong suot"))
                    .findFirst()
                    .orElse(null);

            String metalPrice = metal != null ? formatPrice(metal.getPrice()) : "chưa cập nhật";
            String invisPrice = invis != null ? formatPrice(invis.getPrice()) : "chưa cập nhật";

            if (invisFirst) {
                return "Tôi gợi ý 2 lựa chọn chỉnh nha phù hợp cho bạn. "
                        + "Invisalign có ưu điểm thẩm mỹ hơn, khay trong suốt, khó bị phát hiện, dễ tháo lắp, phù hợp người giao tiếp nhiều nhưng chi phí thường cao hơn "
                        + "(" + invisPrice + "). "
                        + "Niềng răng kim loại thường phù hợp hơn với các ca phức tạp, lực kéo ổn định và chi phí thường thấp hơn "
                        + "(" + metalPrice + "). "
                        + "Bạn hãy chọn 1 trong 2 dịch vụ, sau đó chọn nhanh một khung giờ bên dưới.";
            }

            return "Tôi gợi ý 2 lựa chọn chỉnh nha phù hợp cho bạn. "
                    + "Niềng răng kim loại thường phù hợp hơn với các ca phức tạp, lực kéo ổn định và chi phí thường thấp hơn "
                    + "(" + metalPrice + "). "
                    + "Invisalign có ưu điểm thẩm mỹ hơn, khay trong suốt, dễ tháo lắp, phù hợp người giao tiếp nhiều nhưng chi phí thường cao hơn "
                    + "(" + invisPrice + "). "
                    + "Bạn hãy chọn 1 trong 2 dịch vụ, sau đó chọn nhanh một khung giờ bên dưới.";
        }

        String serviceNames = response.getServices().stream()
                .map(AIServiceSuggestionDto::getName)
                .collect(Collectors.joining(", "));

        return "Tôi gợi ý dịch vụ: " + serviceNames
                + ". Bạn hãy chọn 1 dịch vụ phù hợp nhất, sau đó chọn nhanh một khung giờ bên dưới.";
    }

    private String normalizeText(String input) {
        if (input == null) return "";
        return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .trim();
    }

    private String formatPrice(double price) {
        return String.format("%,.0f VNĐ", price);
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
                                                            boolean excludeExactRequestedTime) {
        if (slots == null || slots.isEmpty()) return List.of();

        List<SlotDto> prioritized = prioritizeByPreferredTime(slots, preferredTime);

        if (!excludeExactRequestedTime || preferredTime == null || preferredTime.isBlank()) {
            return prioritized;
        }

        try {
            LocalTime target = LocalTime.parse(preferredTime);
            return prioritized.stream()
                    .filter(slot -> slot.getStartTime() == null || !slot.getStartTime().equals(target))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return prioritized;
        }
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

    private int getPrimaryDurationMinutes(Services primaryService) {
        if (primaryService == null || primaryService.getDurationMinutes() <= 0) {
            return 30;
        }
        return primaryService.getDurationMinutes();
    }

    private String calculateSuggestedEndTime(SlotDto slot, int durationMinutes) {
        if (slot == null || slot.getStartTime() == null) return "";
        return slot.getStartTime().plusMinutes(durationMinutes).toString();
    }

    private String buildDisplayText(LocalDate date, SlotDto slot, int durationMinutes) {
        if (slot == null || slot.getStartTime() == null) return String.valueOf(date);

        String start = slot.getStartTime().toString();
        String end = slot.getStartTime().plusMinutes(durationMinutes).toString();

        return date + " | " + start + " - " + end;
    }

    private boolean containsAnyText(String text, String... tokens) {
        if (text == null) return false;
        for (String token : tokens) {
            if (text.contains(normalizeText(token))) {
                return true;
            }
        }
        return false;
    }
}
