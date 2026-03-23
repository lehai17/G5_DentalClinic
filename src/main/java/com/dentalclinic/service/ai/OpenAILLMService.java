package com.dentalclinic.service.ai;

import com.dentalclinic.dto.ai.LLMBookingInterpretation;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.ServiceRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.text.Normalizer;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class OpenAILLMService implements LLMService {

    private static final String KEY_GENERAL_EXAM = "GENERAL_EXAM";
    private static final String KEY_SCALING = "SCALING";
    private static final String KEY_WISDOM_TOOTH = "WISDOM_TOOTH_EXTRACTION";
    private static final String KEY_WHITENING = "WHITENING";
    private static final String KEY_FILLING = "FILLING";
    private static final String KEY_ROOT_CANAL = "ROOT_CANAL";
    private static final String KEY_IMPLANT = "IMPLANT";
    private static final String KEY_CERCON_CROWN = "CERCON_CROWN";
    private static final String KEY_METAL_BRACES = "METAL_BRACES";
    private static final String KEY_INVISALIGN = "INVISALIGN";
    private static final String KEY_TOOTH_JEWELRY = "TOOTH_JEWELRY";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ServiceRepository serviceRepository;

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.url}")
    private String apiUrl;

    @Value("${ai.openai.model}")
    private String model;

    public OpenAILLMService(RestTemplate restTemplate, ServiceRepository serviceRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.serviceRepository = serviceRepository;
    }

    @Override
    public LLMBookingInterpretation interpretBookingRequest(String userMessage) {
        try {
            String today = LocalDate.now().toString();
            String serviceCatalog = buildServiceCatalogPrompt();

            String prompt = """
Bạn là trợ lý AI đặt lịch cho nha khoa.

Mục tiêu:
- Chỉ suy luận NHU CẦU DỊCH VỤ để đặt lịch.
- Không chẩn đoán bệnh.
- Chỉ trả về đúng JSON, không thêm markdown, không giải thích.
- Chỉ chọn các nhóm dịch vụ phù hợp nhất với câu người dùng.
- Nếu câu người dùng mô tả rất rõ triệu chứng thì ưu tiên nhóm dịch vụ chuyên biệt, không ưu tiên GENERAL_EXAM.
- Chỉ dùng GENERAL_EXAM khi mô tả quá chung chung, mơ hồ hoặc chưa đủ cơ sở chọn nhóm cụ thể.

Danh sách nhóm dịch vụ hợp lệ cho serviceKeywords:
- GENERAL_EXAM
- SCALING
- WISDOM_TOOTH_EXTRACTION
- WHITENING
- FILLING
- ROOT_CANAL
- IMPLANT
- CERCON_CROWN
- METAL_BRACES
- INVISALIGN
- TOOTH_JEWELRY

Catalog dịch vụ hiện có trong hệ thống:
%s

Quy tắc suy luận:
1. Nếu khách mô tả các vấn đề chỉnh nha / niềng răng như:
   - sai khớp cắn, khớp cắn lệch, khớp cắn không chuẩn
   - răng mọc lệch, răng lệch, chen chúc, khấp khểnh, răng xoay, răng chồng chéo, răng không đều, răng lộn xộn
   - thiếu chỗ trên cung hàm, cung hàm hẹp
   - răng thưa, khe thưa giữa các răng, thưa răng cửa
   - hô răng, vẩu, răng chìa, răng cửa chìa ra trước, overjet tăng, hô hàm
   - móm, khớp cắn ngược, underbite
   - cắn sâu, overbite quá mức, cắn phủ quá nhiều
   - cắn hở, open bite
   - cắn chéo, crossbite, cắn đối đầu, lệch đường giữa
   - răng mọc ngầm, mọc kẹt, mọc sai vị trí, mọc lạc chỗ
   - muốn chỉnh khớp cắn, kéo đều răng, sắp đều răng, đóng khe thưa, chỉnh hô, chỉnh móm, chỉnh cắn sâu, chỉnh cắn hở, chỉnh cắn chéo

2. Quy tắc chọn dịch vụ chỉnh nha:
   - Nếu người dùng nói rõ niềng răng kim loại / mắc cài / mắc cài kim loại -> chỉ trả về ["METAL_BRACES"].
   - Nếu người dùng nói rõ niềng trong suốt / khay trong / khay trong suốt / Invisalign -> chỉ trả về ["INVISALIGN"].
   - Nếu người dùng chỉ mô tả triệu chứng chỉnh nha nhưng chưa chốt phương pháp -> trả về 2 nhóm ["METAL_BRACES", "INVISALIGN"].
   - Nếu ca nặng / phức tạp như móm, cắn sâu, cắn hở, cắn chéo, lệch hàm, chen chúc nặng, sai khớp cắn rõ, răng mọc ngầm / mọc kẹt / lệch nhiều -> xếp METAL_BRACES trước INVISALIGN.
   - Nếu người dùng ưu tiên thẩm mỹ, ít lộ, tháo lắp được, giao tiếp nhiều -> xếp INVISALIGN trước METAL_BRACES.
3. Với các dịch vụ KHÔNG PHẢI chỉnh nha, hãy cố gắng chọn ra 1 nhóm dịch vụ phù hợp nhất duy nhất.
   - Chỉ trả GENERAL_EXAM khi mô tả quá chung, chưa đủ cơ sở, hoặc triệu chứng chưa đủ để phân biệt.
   - Nếu mô tả nói rất rõ nhu cầu như nhổ răng khôn, làm trắng răng, trám răng, điều trị tủy, cấy implant, bọc sứ, đính đá răng thì ưu tiên trả đúng 1 serviceKeyword tương ứng.
   - Nếu người dùng mô tả:
     + đau răng khôn, đau răng số 8, mọc lệch, mọc ngầm, sưng lợi trùm -> WISDOM_TOOTH_EXTRACTION
     + răng ố vàng, xỉn màu, muốn trắng sáng hơn -> WHITENING
     + sâu răng nhẹ/vừa, lỗ sâu nhỏ, mẻ nhỏ, sứt nhỏ, muốn trám -> FILLING
     + đau dữ dội, đau tự phát, đau về đêm, ê buốt kéo dài, viêm tủy, áp xe -> ROOT_CANAL
     + mất răng, gãy răng mất chân, muốn trồng răng, cấy implant -> IMPLANT
     + răng vỡ lớn, bể lớn, muốn bọc sứ, phục hình sứ -> CERCON_CROWN
     + muốn đính đá răng, gắn đá răng -> TOOTH_JEWELRY
     + chỉ nói khám, kiểm tra, tư vấn, đau răng chung chung, ê buốt chung chung -> GENERAL_EXAM
4. Nếu khách mô tả mất răng, rụng răng, gãy răng mất chân, muốn trồng răng, cấy implant -> IMPLANT.
   - Nếu chỉ nói lung lay, sắp rụng, chưa chắc đã mất răng -> ưu tiên GENERAL_EXAM.
5. Nếu khách mô tả bọc sứ, răng vỡ lớn, răng bể lớn, muốn phục hình răng sứ -> CERCON_CROWN.
6. Nếu khách mô tả cao răng, vôi răng, lấy cao, chảy máu chân răng, hôi miệng, vệ sinh răng -> SCALING.
7. Nếu khách mô tả đau răng khôn, răng khôn mọc lệch, mọc ngầm, sưng vùng răng khôn, nhổ răng khôn -> WISDOM_TOOTH_EXTRACTION.
8. Nếu khách mô tả răng ố vàng, xỉn màu, muốn làm trắng -> WHITENING.
9. Nếu khách mô tả đính đá răng, gắn đá răng -> TOOTH_JEWELRY.
10. Nếu mô tả chung chung, mơ hồ, hoặc không chắc -> GENERAL_EXAM.
11. Nếu câu nói hợp nhiều nhóm, có thể trả về tối đa 3 serviceKeywords, sắp theo mức độ phù hợp giảm dần.
12. Nếu người dùng nói "mai", "ngày kia", "sau 3 ngày nữa", "tuần sau", "cuối tuần", hãy quy đổi preferredDate thành yyyy-MM-dd.
13. Nếu người dùng nói "14 giờ", "2 giờ chiều", "14:30", hãy quy đổi preferredTime thành HH:mm.
14. timePreference chỉ nhận: morning, afternoon, evening, any.
15. urgency chỉ nhận: low, medium, high.

Ngày hiện tại: %s

JSON schema:
{
  "intent": "BOOK_APPOINTMENT",
  "serviceKeywords": ["GENERAL_EXAM"],
  "preferredDate": "yyyy-MM-dd hoặc rỗng",
  "preferredTime": "HH:mm hoặc rỗng",
  "timePreference": "morning|afternoon|evening|any",
  "urgency": "low|medium|high",
  "normalizedMessage": "..."
}

Tin nhắn khách hàng:
%s
""".formatted(serviceCatalog, today, userMessage == null ? "" : userMessage);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                    "role", "system",
                    "content", "Bạn là trợ lý AI đặt lịch nha khoa, chuyên suy luận nhu cầu dịch vụ từ mô tả của khách hàng."
            ));
            messages.add(Map.of("role", "user", "content", prompt));
            body.put("messages", messages);
            body.put("temperature", 0.1);

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
            LLMBookingInterpretation parsed = objectMapper.readValue(content, LLMBookingInterpretation.class);
            sanitizeInterpretation(parsed, userMessage);
            return parsed;

        } catch (Exception ex) {
            return fallbackInterpretation(userMessage);
        }
    }

    private String buildServiceCatalogPrompt() {
        List<Services> services = serviceRepository.findByActiveTrue();
        if (services == null || services.isEmpty()) {
            return "- Không có dữ liệu catalog dịch vụ.";
        }

        return services.stream()
                .map(s -> "- " + safeText(s.getName()) +
                        (safeText(s.getDescription()).isBlank() ? "" : " | " + safeText(s.getDescription())))
                .collect(Collectors.joining("\n"));
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    private String extractContent(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IllegalStateException("LLM response content is empty");
        }
        return contentNode.asText();
    }

    private void sanitizeInterpretation(LLMBookingInterpretation interpretation, String originalMessage) {
        if (interpretation == null) {
            return;
        }

        if (interpretation.getIntent() == null || interpretation.getIntent().isBlank()) {
            interpretation.setIntent("BOOK_APPOINTMENT");
        }

        if (interpretation.getNormalizedMessage() == null || interpretation.getNormalizedMessage().isBlank()) {
            interpretation.setNormalizedMessage(originalMessage == null ? "" : originalMessage);
        }

        interpretation.setPreferredDate(safeDate(interpretation.getPreferredDate(), originalMessage));
        interpretation.setPreferredTime(safeTime(interpretation.getPreferredTime(), originalMessage));
        interpretation.setTimePreference(safeTimePreference(interpretation.getTimePreference(), originalMessage));
        interpretation.setUrgency(safeUrgency(interpretation.getUrgency(), originalMessage));

        List<String> normalizedKeywords = sanitizeKeywords(interpretation.getServiceKeywords(), originalMessage);
        interpretation.setServiceKeywords(refineKeywordsBySymptoms(normalizedKeywords, originalMessage));
    }

    private String safeDate(String preferredDate, String originalMessage) {
        if (preferredDate != null && preferredDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return preferredDate;
        }
        return resolveRelativeDate(normalize(originalMessage));
    }

    private String safeTime(String preferredTime, String originalMessage) {
        if (preferredTime != null && preferredTime.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            return preferredTime;
        }
        return resolvePreferredTime(normalize(originalMessage));
    }

    private String safeTimePreference(String timePreference, String originalMessage) {
        if (timePreference != null) {
            String value = timePreference.trim().toLowerCase(Locale.ROOT);
            if (value.equals("morning") || value.equals("afternoon") || value.equals("evening") || value.equals("any")) {
                return value;
            }
        }
        return inferTimePreference(normalize(originalMessage));
    }

    private String safeUrgency(String urgency, String originalMessage) {
        if (urgency != null) {
            String value = urgency.trim().toLowerCase(Locale.ROOT);
            if (value.equals("low") || value.equals("medium") || value.equals("high")) {
                return value;
            }
        }

        String lower = normalize(originalMessage);
        if (containsAny(lower, "rat dau", "dau du doi", "sung to", "chay mau nhieu", "khan cap", "cap cuu", "mat ngu vi dau")) {
            return "high";
        }
        if (containsAny(lower, "dau rang", "e buot", "nhuc", "kho chiu", "viem")) {
            return "medium";
        }
        return "low";
    }

    private List<String> sanitizeKeywords(List<String> serviceKeywords, String originalMessage) {
        List<String> cleaned = new ArrayList<>();
        if (serviceKeywords != null) {
            for (String keyword : serviceKeywords) {
                String canonical = canonicalKeyword(keyword);
                if (canonical != null && !cleaned.contains(canonical)) {
                    cleaned.add(canonical);
                }
            }
        }

        if (cleaned.isEmpty()) {
            return fallbackInterpretation(originalMessage).getServiceKeywords();
        }

        if (cleaned.size() > 3) {
            return new ArrayList<>(cleaned.subList(0, 3));
        }
        return cleaned;
    }

    private List<String> refineKeywordsBySymptoms(List<String> llmKeywords, String originalMessage) {
        String raw = normalize(originalMessage);

        boolean orthoSymptom = isOrthodonticSymptom(raw);
        boolean severeOrtho = isSevereOrthodonticCase(raw);
        boolean preferInvisalign = isInvisalignPreference(raw);
        boolean preferMetal = isMetalPreference(raw);

        if (orthoSymptom) {
            List<String> orthoResult = new ArrayList<>();

            if (preferMetal && !preferInvisalign) {
                orthoResult.add(KEY_METAL_BRACES);
                return orthoResult;
            }

            if (preferInvisalign && !preferMetal) {
                orthoResult.add(KEY_INVISALIGN);
                return orthoResult;
            }

            if (preferInvisalign && !severeOrtho) {
                orthoResult.add(KEY_INVISALIGN);
                orthoResult.add(KEY_METAL_BRACES);
                return orthoResult;
            }

            orthoResult.add(KEY_METAL_BRACES);
            orthoResult.add(KEY_INVISALIGN);
            return orthoResult;
        }

        String direct = decidePrimaryServiceKeyword(raw, llmKeywords);
        if (direct != null) {
            return List.of(direct);
        }

        String llmTop = firstSpecificKeyword(llmKeywords);
        if (llmTop != null) {
            return List.of(llmTop);
        }

        return List.of(KEY_GENERAL_EXAM);
    }

    private LLMBookingInterpretation fallbackInterpretation(String userMessage) {
        LLMBookingInterpretation fallback = new LLMBookingInterpretation();
        fallback.setIntent("BOOK_APPOINTMENT");
        fallback.setNormalizedMessage(userMessage == null ? "" : userMessage);

        String lower = normalize(userMessage);

        fallback.setPreferredTime(resolvePreferredTime(lower));
        fallback.setPreferredDate(resolveRelativeDate(lower));
        fallback.setTimePreference(inferTimePreference(lower));
        fallback.setUrgency(safeUrgency(null, userMessage));
        fallback.setServiceKeywords(refineKeywordsBySymptoms(List.of(KEY_GENERAL_EXAM), userMessage));
        return fallback;
    }

    private void addScore(Map<String, Integer> scores, String serviceGroup, String lower, String... phrases) {
        int score = scores.getOrDefault(serviceGroup, 0);
        for (String phrase : phrases) {
            String normalizedPhrase = normalize(phrase);
            if (!normalizedPhrase.isBlank() && lower.contains(normalizedPhrase)) {
                score += normalizedPhrase.split("\\s+").length >= 2 ? 3 : 2;
            }
        }
        scores.put(serviceGroup, score);
    }

    private void increaseScore(Map<String, Integer> scores, String key, int delta) {
        scores.put(key, scores.getOrDefault(key, 0) + delta);
    }

    private List<String> buildKeywordsByScore(Map<String, Integer> scores) {
        List<Map.Entry<String, Integer>> ranked = scores.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

        if (ranked.isEmpty() || ranked.get(0).getValue() <= 0) {
            return new ArrayList<>();
        }

        int topScore = ranked.get(0).getValue();
        List<String> result = new ArrayList<>();

        for (Map.Entry<String, Integer> entry : ranked) {
            if (entry.getValue() <= 0) {
                continue;
            }
            if (entry.getValue() >= topScore - 2) {
                result.add(entry.getKey());
            }
            if (result.size() == 3) {
                break;
            }
        }

        return result;
    }

    private String resolveRelativeDate(String lower) {
        LocalDate today = LocalDate.now();

        if (lower == null || lower.isBlank()) {
            return "";
        }

        if (lower.contains("hom nay")) {
            return today.toString();
        }

        if (lower.contains("ngay kia")) {
            return today.plusDays(2).toString();
        }

        if (lower.contains("ngay mai") || lower.contains("mai")) {
            return today.plusDays(1).toString();
        }

        Matcher afterDaysMatcher = Pattern.compile("sau\\s+(\\d+)\\s+ngay").matcher(lower);
        if (afterDaysMatcher.find()) {
            int days = Integer.parseInt(afterDaysMatcher.group(1));
            return today.plusDays(days).toString();
        }

        Matcher onDayMatcher = Pattern.compile("ngay\\s+(\\d+)\\s*/\\s*(\\d+)(?:\\s*/\\s*(\\d{4}))?").matcher(lower);
        if (onDayMatcher.find()) {
            int day = Integer.parseInt(onDayMatcher.group(1));
            int month = Integer.parseInt(onDayMatcher.group(2));
            int year = onDayMatcher.group(3) != null ? Integer.parseInt(onDayMatcher.group(3)) : today.getYear();
            try {
                return LocalDate.of(year, month, day).toString();
            } catch (Exception ignored) {
            }
        }

        if (lower.contains("tuan sau")) {
            return today.plusWeeks(1).toString();
        }

        if (lower.contains("cuoi tuan")) {
            int currentDow = today.getDayOfWeek().getValue();
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

        Matcher hhmmMatcher = Pattern.compile("(\\d{1,2})[:h](\\d{2})").matcher(lower);
        if (hhmmMatcher.find()) {
            int hour = Integer.parseInt(hhmmMatcher.group(1));
            int minute = Integer.parseInt(hhmmMatcher.group(2));
            if (hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59) {
                return String.format("%02d:%02d", hour, minute);
            }
        }

        Matcher hourMatcher = Pattern.compile("(\\d{1,2})\\s*(gio|h)").matcher(lower);
        if (hourMatcher.find()) {
            int hour = Integer.parseInt(hourMatcher.group(1));

            if ((lower.contains("chieu") || lower.contains("toi")) && hour < 12) {
                hour += 12;
            }
            if (lower.contains("sang") && hour == 12) {
                hour = 0;
            }

            if (hour >= 0 && hour <= 23) {
                return String.format("%02d:00", hour);
            }
        }

        return "";
    }

    private String inferTimePreference(String lower) {
        if (lower == null || lower.isBlank()) {
            return "any";
        }

        if (containsAny(lower, "buoi sang", "sang")) {
            return "morning";
        }
        if (containsAny(lower, "buoi chieu", "chieu")) {
            return "afternoon";
        }
        if (containsAny(lower, "buoi toi", "toi")) {
            return "evening";
        }
        return "any";
    }

    private String canonicalKeyword(String rawKeyword) {
        String keyword = normalize(rawKeyword);
        if (keyword.isBlank()) {
            return null;
        }

        if (containsAny(keyword, "general_exam", "general exam", "kham tong quat", "kham", "tu van")) {
            return KEY_GENERAL_EXAM;
        }
        if (containsAny(keyword, "scaling", "cao voi", "cao rang", "lay cao", "ve sinh rang")) {
            return KEY_SCALING;
        }
        if (containsAny(keyword, "wisdom_tooth_extraction", "nho rang khon", "rang khon", "rang so 8")) {
            return KEY_WISDOM_TOOTH;
        }
        if (containsAny(keyword, "whitening", "tay trang", "lam trang")) {
            return KEY_WHITENING;
        }
        if (containsAny(keyword, "filling", "tram", "sau rang", "lo rang")) {
            return KEY_FILLING;
        }
        if (containsAny(keyword, "root_canal", "tuy", "dieu tri tuy", "viem tuy")) {
            return KEY_ROOT_CANAL;
        }
        if (containsAny(keyword, "implant", "cay ghep", "trong rang", "mat rang", "rung rang")) {
            return KEY_IMPLANT;
        }
        if (containsAny(keyword, "cercon_crown", "cercon", "boc su", "rang su", "phuc hinh")) {
            return KEY_CERCON_CROWN;
        }
        if (containsAny(keyword,
                "nieng rang kim loai", "mac cai", "kim loai", "mac cai kim loai")) {
            return KEY_METAL_BRACES;
        }

        if (containsAny(keyword,
                "invisalign", "nieng trong suot", "khay trong", "khay trong suot")) {
            return KEY_INVISALIGN;
        }
        if (containsAny(keyword, "tooth_jewelry", "dinh da", "gan da", "da rang", "gan da rang")) {
            return KEY_TOOTH_JEWELRY;
        }
        return null;
    }

    private boolean containsAny(String text, String... tokens) {
        String normalizedText = normalize(text);
        for (String token : tokens) {
            if (normalizedText.contains(normalize(token))) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String input) {
        if (input == null) {
            return "";
        }

        String noAccent = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D');

        return noAccent
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private boolean containsAnyLoose(String text, String... phrases) {
        for (String phrase : phrases) {
            if (containsPhraseLoose(text, phrase)) {
                return true;
            }
        }
        return false;
    }

    private boolean containsPhraseLoose(String text, String phrase) {
        List<String> textTokens = tokenizeLoose(text);
        List<String> phraseTokens = tokenizeLoose(phrase);

        if (textTokens.isEmpty() || phraseTokens.isEmpty()) {
            return false;
        }

        Set<String> textSet = new LinkedHashSet<>(textTokens);
        for (String token : phraseTokens) {
            if (!textSet.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private List<String> tokenizeLoose(String input) {
        String normalized = normalize(input).replaceAll("[^a-z0-9\\s]", " ");
        if (normalized.isBlank()) {
            return List.of();
        }

        return Arrays.stream(normalized.split("\\s+"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    private boolean isOrthodonticSymptom(String raw) {
        return containsAnyLoose(raw,
                "nieng rang", "chinh nha",

                "sai khop can", "khop can lech", "khop can khong chuan",
                "chinh khop can", "muon chinh khop can",

                "rang lech", "rang moc lech", "rang sai vi tri", "rang moc sai vi tri",
                "rang moc lac cho", "rang moc ngam", "rang moc ket", "rang mai khong moc len",

                "chen chuc", "rang chen chuc", "rang moc chen chuc",
                "khap khenh",
                "rang xoay", "rang bi xoay",
                "rang chong cheo", "rang chong len nhau",
                "rang khong deu",
                "rang lon xon", "rang moc lon xon",

                "muon rang deu hon", "muon lam deu rang",
                "muon keo deu rang", "muon sap deu rang",
                "muon chinh rang", "muon sua rang",

                "thieu cho tren cung ham", "cung ham hep",

                "rang thua", "ke rang thua", "khe thua", "khe ho", "rang co khe ho", "thua rang cua",
                "muon dong khe thua",

                "rang ho", "ho rang", "ho ham",
                "rang vau", "vau",
                "rang chia",
                "rang cua dua ra ngoai", "rang cua chia ra ngoai",
                "rang tren chia ra nhieu", "ham tren nho ra", "overjet",
                "muon chinh ho",

                "rang mom", "mom", "ham duoi dua ra truoc",
                "khop can nguoc", "underbite",
                "muon chinh mom",

                "can sau", "khop can sau", "overbite", "can phu qua nhieu",
                "rang tren phu het rang duoi", "rang can vao nuou", "rang can vao vom mieng",
                "muon chinh can sau",

                "can ho", "open bite", "rang truoc khong cham nhau", "rang sau khong cham nhau",
                "can lai van ho", "ngam mieng ma rang khong cham",
                "muon chinh can ho",

                "can cheo", "crossbite", "can cheo truoc", "can cheo sau", "can doi dau",
                "muon chinh can cheo",

                "lech duong giua", "duong giua rang bi lech",
                "lech ham", "lech ham chuc nang", "sai lech tuong quan 2 ham",
                "ham tren hep", "ham duoi lech", "mat can doi xuong ham",

                "rang moc chen ra ngoai cung", "rang moc cup vao trong", "rang vinh vien moc lech",
                "rang sua ton tai lau gay lech rang", "mat rang sua som gay xo lech rang",
                "rang bi xo lech sau nho rang", "rang ngay cang xo lech", "rang di chuyen", "xe dich",

                "rang cua khong khep duoc", "moi khong khep kin", "moi khong khep kin do rang ho",
                "cuoi thay rang chia ra",

                "roi loan khop can do thoi quen xau", "sai khop can do mut tay",
                "sai khop can do day luoi", "ngam ti gia lau",
                "sai khop can do nghien rang", "lech khop can do chan thuong",

                "thieu rang bam sinh", "thua rang", "rang nanh moc ngam", "rang nanh moc lech",

                "nhai bi lech mot ben", "nhai kho vi rang khong khop",
                "kho can thuc an", "can do an khong dut", "nhai khong deu", "khop can la",

                "lam dep rang bang nieng"
        );
    }

    private boolean isSevereOrthodonticCase(String raw) {
        return containsAnyLoose(raw,
                "mom", "khop can nguoc", "underbite",
                "can sau", "khop can sau", "overbite",
                "can ho", "open bite",
                "can cheo", "crossbite",
                "lech ham", "sai khop can", "khop can lech",
                "chen chuc nang", "rang moc ngam", "rang moc ket",
                "rang moc lac cho", "rang nanh moc ngam", "rang nanh moc lech",
                "mat can doi xuong ham", "khe ho moi vom mieng"
        );
    }

    private boolean isInvisalignPreference(String raw) {
        return containsAnyLoose(raw,
                "invisalign", "khay trong", "khay trong suot",
                "nieng trong suot", "nieng rang trong suot",
                "nieng tham my", "tham my hon", "phuong phap tham my", "chinh nha tham my",
                "it lo", "kin dao", "kin dao hon",
                "de thao lap", "de thao ra",
                "giao tiep nhieu", "hay gap khach hang",
                "it bi phat hien", "kho bi phat hien", "kho nhan ra", "khong muon lo"
        );
    }

    private boolean isMetalPreference(String raw) {
        return containsAnyLoose(raw,
                "kim loai", "mac cai", "nieng kim loai",
                "nieng rang kim loai", "mac cai kim loai",
                "mac cai thuong", "nieng rang truyen thong"
        );
    }

    private String decidePrimaryServiceKeyword(String raw, List<String> llmKeywords) {
        if (isToothJewelrySymptom(raw)) {
            return KEY_TOOTH_JEWELRY;
        }
        if (isWhiteningSymptom(raw)) {
            return KEY_WHITENING;
        }
        if (isWisdomToothSymptom(raw)) {
            return KEY_WISDOM_TOOTH;
        }
        if (isImplantSymptom(raw)) {
            return KEY_IMPLANT;
        }
        if (isRootCanalSymptom(raw)) {
            return KEY_ROOT_CANAL;
        }
        if (isCerconCrownSymptom(raw)) {
            return KEY_CERCON_CROWN;
        }
        if (isFillingSymptom(raw)) {
            return KEY_FILLING;
        }
        if (isScalingSymptom(raw)) {
            return KEY_SCALING;
        }

        if (isGeneralExamSymptom(raw)) {
            return KEY_GENERAL_EXAM;
        }

        String llmTop = firstSpecificKeyword(llmKeywords);
        if (llmTop != null) {
            return llmTop;
        }

        return null;
    }

    private String firstSpecificKeyword(List<String> llmKeywords) {
        if (llmKeywords == null || llmKeywords.isEmpty()) {
            return null;
        }

        for (String key : llmKeywords) {
            String canonical = canonicalKeyword(key);
            if (canonical != null && !KEY_GENERAL_EXAM.equals(canonical)) {
                return canonical;
            }
        }
        return null;
    }

    private boolean isGeneralExamSymptom(String raw) {
        return containsAnyLoose(raw,
                "kham rang", "kham tong quat", "kham", "kiem tra rang", "tu van",
                "dau rang", "e buot", "nhay cam", "lung lay", "sap rung",
                "viem nuou", "sung nuou", "chay mau chan rang", "hoi mieng"
        );
    }

    private boolean isScalingSymptom(String raw) {
        return containsAnyLoose(raw,
                "cao voi", "cao rang", "voi rang", "lay cao",
                "ve sinh rang", "cao voi rang",
                "chay mau chan rang", "hoi mieng", "viem nuou", "mang bam"
        );
    }

    private boolean isWisdomToothSymptom(String raw) {
        return containsAnyLoose(raw,
                "rang khon", "rang so 8", "nho rang khon", "nho rang so 8",
                "dau rang khon", "sung rang khon", "sung loi trum",
                "rang khon moc lech", "rang khon moc ngam",
                "dau cuoi ham", "dau goc ham", "ha mieng dau"
        );
    }

    private boolean isWhiteningSymptom(String raw) {
        return containsAnyLoose(raw,
                "tay trang rang", "lam trang rang", "rang trang hon",
                "rang o vang", "rang xi mau", "rang vang", "rang ngam mau",
                "trang rang", "rang bi vang", "rang bi xi mau"
        );
    }

    private boolean isFillingSymptom(String raw) {
        return containsAnyLoose(raw,
                "tram rang", "rang sau", "lo rang", "lo sau",
                "rang bi thung", "me rang", "sut rang", "vo nho",
                "rang sau nhe", "den mat rang", "den mat nhai",
                "thuc an giat vao lo rang"
        );
    }

    private boolean isRootCanalSymptom(String raw) {
        return containsAnyLoose(raw,
                "dieu tri tuy", "lay tuy", "viem tuy", "tuy rang",
                "dau rang du doi", "dau du doi", "dau ve dem", "mat ngu vi dau rang",
                "e buot keo dai", "dau sau trong rang", "dau giat theo con",
                "go vao rang dau", "ap xe", "sung mu", "rang chet tuy"
        );
    }

    private boolean isImplantSymptom(String raw) {
        return containsAnyLoose(raw,
                "implant", "cay ghep implant", "trong rang", "trong lai rang",
                "mat rang", "rụng rang", "gay rang mat chan", "mat chan rang",
                "nho rang xong muon trong lai", "phuc hoi cho mat rang", "mat rang lau nam"
        );
    }

    private boolean isCerconCrownSymptom(String raw) {
        return containsAnyLoose(raw,
                "boc su", "rang su", "boc rang su", "cercon", "phuc hinh rang",
                "rang vo lon", "rang be lon", "vo lon", "me lon",
                "rang yeu sau lay tuy", "rang sau dieu tri tuy muon boc su",
                "muon lam rang su", "muon boc rang su"
        );
    }

    private boolean isToothJewelrySymptom(String raw) {
        return containsAnyLoose(raw,
                "dinh da rang", "gan da rang", "dinh da", "gan da",
                "gan charm rang", "lam dep rang bang da"
        );
    }
}