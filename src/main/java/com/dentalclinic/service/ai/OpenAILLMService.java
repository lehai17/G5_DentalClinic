package com.dentalclinic.service.ai;

import com.dentalclinic.dto.ai.LLMBookingInterpretation;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.util.*;

@Service
public class OpenAILLMService implements LLMService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.url}")
    private String apiUrl;

    @Value("${ai.openai.model}")
    private String model;

    private String resolveRelativeDate(String lower) {
        LocalDate today = LocalDate.now();

        if (lower == null || lower.isBlank()) {
            return "";
        }

        if (lower.contains("hôm nay")) {
            return today.toString();
        }

        if (lower.contains("ngày mai") || lower.contains("mai")) {
            return today.plusDays(1).toString();
        }

        if (lower.contains("ngày kia")) {
            return today.plusDays(2).toString();
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("sau\\s+(\\d+)\\s+ngày")
                .matcher(lower);
        if (matcher.find()) {
            int days = Integer.parseInt(matcher.group(1));
            return today.plusDays(days).toString();
        }

        if (lower.contains("tuần sau")) {
            return today.plusWeeks(1).toString();
        }

        if (lower.contains("cuối tuần")) {
            int currentDow = today.getDayOfWeek().getValue(); // Mon=1 ... Sun=7
            int daysUntilSaturday = 6 - currentDow;
            if (daysUntilSaturday < 0) {
                daysUntilSaturday += 7;
            }
            return today.plusDays(daysUntilSaturday).toString();
        }

        return "";
    }

    private String resolvePreferredTime(String lower) {
        if (lower == null || lower.isBlank()) {
            return "";
        }

        java.util.regex.Matcher hhmmMatcher = java.util.regex.Pattern
                .compile("(\\d{1,2}):(\\d{2})")
                .matcher(lower);
        if (hhmmMatcher.find()) {
            int hour = Integer.parseInt(hhmmMatcher.group(1));
            int minute = Integer.parseInt(hhmmMatcher.group(2));
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return String.format("%02d:%02d", hour, minute);
            }
        }

        java.util.regex.Matcher hourMatcher = java.util.regex.Pattern
                .compile("(\\d{1,2})\\s*giờ")
                .matcher(lower);
        if (hourMatcher.find()) {
            int hour = Integer.parseInt(hourMatcher.group(1));

            if (lower.contains("chiều") && hour < 12) {
                hour += 12;
            } else if (lower.contains("tối") && hour < 12) {
                hour += 12;
            }

            if (hour >= 0 && hour <= 23) {
                return String.format("%02d:00", hour);
            }
        }

        return "";
    }

    public OpenAILLMService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public LLMBookingInterpretation interpretBookingRequest(String userMessage) {
        try {
            String today = LocalDate.now().toString();

            String prompt = """
    Bạn là trợ lý đặt lịch cho nha khoa.
    Nhiệm vụ:
    1. Phân tích nhu cầu đặt lịch của khách hàng.
    2. Không chẩn đoán bệnh.
    3. Trả về đúng JSON, không thêm markdown.
    4. Nếu người dùng nói "mai", "ngày kia", "sau 3 ngày nữa", "tuần sau", "cuối tuần", hãy quy đổi thành ngày cụ thể theo định dạng yyyy-MM-dd.
    5. Nếu người dùng nói "14 giờ", "2 giờ chiều", "14:30", hãy quy đổi thành preferredTime theo định dạng HH:mm.

    Ngày hiện tại: %s

    JSON schema:
    {
      "intent": "BOOK_APPOINTMENT",
      "serviceKeywords": ["..."],
      "preferredDate": "yyyy-MM-dd hoặc rỗng",
      "preferredTime": "HH:mm hoặc rỗng",
      "timePreference": "morning|afternoon|evening|any",
      "urgency": "low|medium|high",
      "normalizedMessage": "..."
    }

    Tin nhắn khách hàng:
    %s
    """.formatted(today, userMessage);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", "Bạn là trợ lý AI đặt lịch nha khoa."));
            messages.add(Map.of("role", "user", "content", prompt));
            body.put("messages", messages);
            body.put("temperature", 0.2);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            String content = extractContent(response.getBody());
            return objectMapper.readValue(content, LLMBookingInterpretation.class);

        } catch (Exception ex) {
            return fallbackInterpretation(userMessage);
        }
    }

    private String extractContent(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IllegalStateException("LLM response content is empty");
        }
        return contentNode.asText();
    }

    private LLMBookingInterpretation fallbackInterpretation(String userMessage) {
        LLMBookingInterpretation fallback = new LLMBookingInterpretation();
        fallback.setIntent("BOOK_APPOINTMENT");
        fallback.setUrgency("medium");
        fallback.setNormalizedMessage(userMessage);

        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT);

        fallback.setPreferredTime(resolvePreferredTime(lower));

        List<String> keywords = new ArrayList<>();

        if (lower.contains("cạo vôi răng") || lower.contains("cạo vôi") || lower.contains("cao răng") || lower.contains("lấy cao")) {
            keywords.add("cạo vôi răng");
        } else if (lower.contains("nhổ răng khôn")) {
            keywords.add("nhổ răng khôn");
        } else if (lower.contains("niềng") || lower.contains("chỉnh nha")) {
            keywords.add("chỉnh nha");
        } else if (lower.contains("tẩy trắng")) {
            keywords.add("tẩy trắng");
        } else if (lower.contains("trám")) {
            keywords.add("trám răng");
        } else if (lower.contains("đau răng") || lower.contains("ê buốt") || lower.contains("khám")) {
            keywords.add("khám tổng quát");
        } else {
            keywords.add("khám tổng quát");
        }

        if (lower.contains("chiều")) {
            fallback.setTimePreference("afternoon");
        } else if (lower.contains("sáng")) {
            fallback.setTimePreference("morning");
        } else if (lower.contains("tối")) {
            fallback.setTimePreference("evening");
        } else {
            fallback.setTimePreference("any");
        }

        fallback.setPreferredDate(resolveRelativeDate(lower));
        fallback.setServiceKeywords(keywords);
        return fallback;
    }
}