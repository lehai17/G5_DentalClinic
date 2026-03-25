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

    // Khởi tạo service AI booking với bộ phân tích LLM, bộ match dịch vụ và service lấy lịch trống
    public AIBookingServiceImpl(LLMService llmService,
                                ServiceMatcher serviceMatcher,
                                CustomerAppointmentService customerAppointmentService) {
        this.llmService = llmService;
        this.serviceMatcher = serviceMatcher;
        this.customerAppointmentService = customerAppointmentService;
    }

    @Override
    // Xử lý toàn bộ luồng AI booking: hiểu nhu cầu, match dịch vụ, lấy slot và trả gợi ý cuối cùng
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

        LocalDate preferredDate = resolvePreferredDate(interpretation.getPreferredDate(), request.getMessage());
        List<SlotDto> slots = customerAppointmentService.getAvailableSlots(userId, primaryServiceIds, preferredDate);

        // Nếu user muốn khám sớm nhất/gần nhất/ngay mà ngày hiện tại chưa có slot,
        // thì thử các ngày gần nhất, ưu tiên hôm nay rồi đến ngày mai
        if (slots.isEmpty() && isEarliestRequest(request.getMessage(), interpretation)) {
            LocalDate fallbackDate = findNearestAvailableDate(userId, primaryServiceIds, 3);
            if (fallbackDate != null) {
                preferredDate = fallbackDate;
                slots = customerAppointmentService.getAvailableSlots(userId, primaryServiceIds, preferredDate);
            }
        }

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
        final LocalDate finalPreferredDate = preferredDate;
        final int finalPrimaryDurationMinutes = primaryDurationMinutes;

        response.setSlotOptions(
                filteredSlots.stream()
                        .limit(maxSuggestions)
                        .map(slot -> new AIBookingOptionDto(
                                slot.getId(),
                                finalPreferredDate.toString(),
                                slot.getStartTime() == null ? "" : slot.getStartTime().toString(),
                                calculateSuggestedEndTime(slot, finalPrimaryDurationMinutes),
                                buildDisplayText(finalPreferredDate, slot, finalPrimaryDurationMinutes)
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

    // Chuẩn hóa ngày mong muốn: nếu user muốn lịch sớm nhất thì ưu tiên hôm nay, nếu không thì dùng ngày AI trả về hoặc mặc định ngày mai
    private LocalDate resolvePreferredDate(String preferredDate, String originalMessage) {
        try {
            if (preferredDate != null && !preferredDate.isBlank()) {
                LocalDate parsed = LocalDate.parse(preferredDate);
                if (!parsed.isBefore(LocalDate.now())) {
                    return parsed;
                }
            }
        } catch (Exception ignored) {
        }

        if (isEarliestText(originalMessage)) {
            return LocalDate.now();
        }

        return LocalDate.now().plusDays(1);
    }

    // Lọc danh sách slot theo buổi người dùng mong muốn: sáng, chiều, tối hoặc bất kỳ
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

    // Tạo câu trả lời hiển thị cho người dùng dựa trên dịch vụ và khung giờ AI đã gợi ý
    private String buildAssistantMessage(AIBookingSuggestionResponse response) {
        if (response.getServices().isEmpty()) {
            return "Tôi chưa xác định rõ dịch vụ phù hợp. Bạn có thể mô tả chi tiết hơn nhu cầu khám.";
        }

        if (response.getSlotOptions().isEmpty()) {
            return "Tôi đã xác định được dịch vụ phù hợp nhưng ngày này chưa có khung giờ trống. Bạn hãy thử ngày khác hoặc khung giờ khác.";
        }

        boolean hasMetal = response.getServices().stream()
                .anyMatch(s -> s.getName() != null && containsAnyText(normalizeText(s.getName()),
                        "kim loai", "mac cai", "mac cai thuong", "truyen thong"));

        boolean hasInvis = response.getServices().stream()
                .anyMatch(s -> s.getName() != null && containsAnyText(normalizeText(s.getName()),
                        "invisalign", "trong suot", "khay trong", "khong mac cai"));

        boolean onlyGeneralExam = response.getServices().size() == 1
                && response.getServices().get(0).getName() != null
                && containsAnyText(
                normalizeText(response.getServices().get(0).getName()),
                "kham tong quat", "tham kham tong quat", "kham rang tong quat"
        );

        String serviceName = response.getServices().get(0).getName();

        if (onlyGeneralExam) {
            return "Tôi gợi ý dịch vụ: " + serviceName
                    + ". Với mô tả hiện tại, tôi chưa đủ cơ sở để xác định chính xác dịch vụ chuyên biệt hơn. "
                    + "Vui lòng liên hệ với phòng khám để được tư vấn chi tiết hơn về tình trạng hiện tại và dịch vụ phù hợp nhất, sau đó chọn nhanh một khung giờ bên dưới.";
        }

        if (hasMetal && hasInvis) {
            AIServiceSuggestionDto first = response.getServices().get(0);
            boolean invisFirst = first.getName() != null && containsAnyText(normalizeText(first.getName()),
                    "invisalign", "trong suot", "khay trong", "khong mac cai");

            AIServiceSuggestionDto metal = response.getServices().stream()
                    .filter(s -> s.getName() != null && containsAnyText(normalizeText(s.getName()),
                            "kim loai", "mac cai", "truyen thong"))
                    .findFirst()
                    .orElse(null);

            AIServiceSuggestionDto invis = response.getServices().stream()
                    .filter(s -> s.getName() != null && containsAnyText(normalizeText(s.getName()),
                            "invisalign", "trong suot", "khay trong", "khong mac cai"))
                    .findFirst()
                    .orElse(null);

            String metalPrice = metal != null ? formatPrice(metal.getPrice()) : "chưa cập nhật";
            String invisPrice = invis != null ? formatPrice(invis.getPrice()) : "chưa cập nhật";

            if (invisFirst) {
                return "Tôi gợi ý 2 lựa chọn chỉnh nha phù hợp cho bạn. "
                        + "Invisalign có ưu điểm thẩm mỹ hơn, khó bị phát hiện, dễ tháo lắp, phù hợp người giao tiếp nhiều nhưng chi phí thường cao hơn "
                        + "(" + invisPrice + "). "
                        + "Niềng răng kim loại có lực kéo ổn định hơn và thường phù hợp hơn với các ca phức tạp, chi phí cũng thấp hơn "
                        + "(" + metalPrice + "). "
                        + "Bạn hãy chọn 1 trong 2 dịch vụ, sau đó chọn nhanh một khung giờ bên dưới.";
            }

            return "Tôi gợi ý 2 lựa chọn chỉnh nha phù hợp cho bạn. "
                    + "Niềng răng kim loại thường phù hợp hơn với các ca phức tạp, lực kéo ổn định và chi phí thấp hơn "
                    + "(" + metalPrice + "). "
                    + "Invisalign có ưu điểm thẩm mỹ hơn, khay trong suốt, dễ tháo lắp, phù hợp người giao tiếp nhiều nhưng chi phí cao hơn "
                    + "(" + invisPrice + "). "
                    + "Bạn hãy chọn 1 trong 2 dịch vụ, sau đó chọn nhanh một khung giờ bên dưới.";
        }

        String serviceNames = response.getServices().stream()
                .map(AIServiceSuggestionDto::getName)
                .collect(Collectors.joining(", "));

        return "Tôi gợi ý dịch vụ: " + serviceNames
                + ". Bạn hãy chọn 1 dịch vụ phù hợp nhất, sau đó chọn nhanh một khung giờ bên dưới.";
    }

    // Chuẩn hóa text: bỏ dấu, viết thường để so khớp từ khóa dễ hơn
    private String normalizeText(String input) {
        if (input == null) return "";
        return java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(java.util.Locale.ROOT)
                .trim();
    }

    // Định dạng giá tiền để hiển thị thân thiện hơn trong câu trả lời
    private String formatPrice(double price) {
        return String.format("%,.0f VNĐ", price);
    }

    // Tìm đúng slot trùng với giờ người dùng yêu cầu, nếu có
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

    // Ưu tiên các slot gần giờ yêu cầu và có thể loại trừ đúng giờ đã bị đặt nếu cần
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

    // Sắp xếp slot theo độ gần với giờ mong muốn để ưu tiên các giờ phù hợp nhất
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

    // Kiểm tra một slot có thực sự còn khả dụng để đặt lịch hay không
    private boolean isSelectableSlot(SlotDto slot) {
        if (slot == null) return false;
        if (slot.isDisabled()) return false;

        if (slot.getAvailableSpots() > 0) {
            return true;
        }

        return slot.isAvailable();
    }

    // Giữ lại chỉ những slot còn đặt được, loại bỏ slot disabled hoặc hết chỗ
    private List<SlotDto> keepSelectableSlots(List<SlotDto> slots) {
        if (slots == null || slots.isEmpty()) return List.of();
        return slots.stream()
                .filter(this::isSelectableSlot)
                .collect(Collectors.toList());
    }

    // Lấy thời lượng mặc định của dịch vụ chính để tính giờ kết thúc gợi ý
    private int getPrimaryDurationMinutes(Services primaryService) {
        if (primaryService == null || primaryService.getDurationMinutes() <= 0) {
            return 30;
        }
        return primaryService.getDurationMinutes();
    }

    // Tính giờ kết thúc dự kiến dựa trên giờ bắt đầu slot và thời lượng dịch vụ
    private String calculateSuggestedEndTime(SlotDto slot, int durationMinutes) {
        if (slot == null || slot.getStartTime() == null) return "";
        return slot.getStartTime().plusMinutes(durationMinutes).toString();
    }

    // Tạo chuỗi hiển thị đẹp cho 1 gợi ý slot, ví dụ: ngày + giờ bắt đầu + giờ kết thúc
    private String buildDisplayText(LocalDate date, SlotDto slot, int durationMinutes) {
        if (slot == null || slot.getStartTime() == null) return String.valueOf(date);

        String start = slot.getStartTime().toString();
        String end = slot.getStartTime().plusMinutes(durationMinutes).toString();
        return date + " | " + start + " - " + end;
    }

    // Kiểm tra text có chứa một trong các từ khóa được truyền vào hay không
    private boolean containsAnyText(String text, String... tokens) {
        if (text == null) return false;
        for (String token : tokens) {
            if (text.contains(normalizeText(token))) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra đây có phải yêu cầu đặt lịch sớm nhất/gần nhất/ngay hay không
    private boolean isEarliestRequest(String originalMessage, LLMBookingInterpretation interpretation) {
        if (isEarliestText(originalMessage)) {
            return true;
        }

        if (interpretation == null) {
            return false;
        }

        String preferredTime = interpretation.getPreferredTime();
        String preferredDate = interpretation.getPreferredDate();

        return (preferredDate == null || preferredDate.isBlank())
                && (preferredTime == null || preferredTime.isBlank())
                && "high".equalsIgnoreCase(interpretation.getUrgency());
    }

    private boolean isEarliestText(String input) {
        String text = normalizeText(input);

        return containsAnyText(text,
                "som nhat",
                "som nhat co the",
                "gan nhat",
                "cang som cang tot",
                "kham som",
                "kham som nhat",
                "dat lich som nhat",
                "kham gan nhat",
                "kham ngay",
                "dat ngay",
                "bay gio",
                "ngay bay gio",
                "hom nay cang som cang tot",
                "trong ngay hom nay");
    }

    // Tìm ngày gần nhất còn slot khả dụng, ưu tiên hôm nay rồi đến các ngày tiếp theo
    private LocalDate findNearestAvailableDate(Long userId, List<Long> serviceIds, int maxDaysToCheck) {
        LocalDate today = LocalDate.now();

        for (int i = 0; i <= maxDaysToCheck; i++) {
            LocalDate candidate = today.plusDays(i);
            List<SlotDto> candidateSlots = customerAppointmentService.getAvailableSlots(userId, serviceIds, candidate);
            List<SlotDto> selectable = keepSelectableSlots(candidateSlots);

            if (!selectable.isEmpty()) {
                return candidate;
            }
        }

        return null;
    }
}