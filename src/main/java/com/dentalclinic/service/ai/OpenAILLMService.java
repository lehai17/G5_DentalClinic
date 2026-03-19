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
3. Nếu khách mô tả đau nhức dữ dội, đau sâu bên trong răng, viêm tủy, đau buốt kéo dài, đau về đêm -> ROOT_CANAL.
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
        String lower = normalize(originalMessage);
        Map<String, Integer> scores = new LinkedHashMap<>();

        for (String key : List.of(
                KEY_GENERAL_EXAM,
                KEY_SCALING,
                KEY_WISDOM_TOOTH,
                KEY_WHITENING,
                KEY_FILLING,
                KEY_ROOT_CANAL,
                KEY_IMPLANT,
                KEY_CERCON_CROWN,
                KEY_METAL_BRACES,
                KEY_INVISALIGN,
                KEY_TOOTH_JEWELRY
        )) {
            scores.put(key, 0);
        }

        if (llmKeywords != null) {
            for (String key : llmKeywords) {
                String canonical = canonicalKeyword(key);
                if (canonical != null) {
                    increaseScore(scores, canonical, canonical.equals(KEY_GENERAL_EXAM) ? 1 : 3);
                }
            }
        }

        addScore(scores, KEY_WISDOM_TOOTH, lower,
                "rang khon", "nho rang khon", "rang so 8",
                "dau rang khon", "sung rang khon", "nho rang");

        addScore(scores, KEY_FILLING, lower,
                "sau rang", "lo rang", "me rang", "sut rang", "vo rang nho", "tram rang", "rang bi thung");

        addScore(scores, KEY_ROOT_CANAL, lower,
                "viem tuy", "tuy rang", "dau tuy", "dau ve dem", "e buot keo dai", "dau sau trong rang");

        addScore(scores, KEY_SCALING, lower,
                "cao voi", "voi rang", "lay cao", "chay mau chan rang", "hoi mieng", "ve sinh rang");

        addScore(scores, KEY_WHITENING, lower,
                "tay trang", "lam trang rang", "rang o vang", "rang xi mau", "xin mau");

        addScore(scores, KEY_IMPLANT, lower,
                "implant", "trong rang", "mat rang", "rung rang", "gay rang mat chan");

        addScore(scores, KEY_CERCON_CROWN, lower,
                "boc su", "rang su", "cercon", "rang vo lon", "rang be lon", "phuc hinh rang");

        addScore(scores, KEY_TOOTH_JEWELRY, lower,
                "dinh da rang", "gan da rang", "dinh da", "gan da", "da rang");

        boolean extractionSymptom = containsAny(lower,
                "nho rang", "rang khon", "nho rang khon",
                "rang so 8", "dau rang khon", "sung rang khon");

        if (extractionSymptom) {
            increaseScore(scores, KEY_WISDOM_TOOTH, 10);
        }

        boolean orthoSymptom = isOrthodonticSymptom(lower);
        boolean severeOrtho = isSevereOrthodonticCase(lower);
        boolean preferInvisalign = isInvisalignPreference(lower);
        boolean preferMetal = isMetalPreference(lower);

        // RAW MESSAGE phải thắng hoàn toàn LLM trong bài toán chỉnh nha
        if (!extractionSymptom && orthoSymptom) {
            List<String> orthoResult = new ArrayList<>();

            if (preferMetal && !preferInvisalign) {
                orthoResult.add(KEY_METAL_BRACES);
                return orthoResult;
            }

            if (preferInvisalign && !preferMetal) {
                orthoResult.add(KEY_INVISALIGN);
                return orthoResult;
            }

            if (severeOrtho) {
                orthoResult.add(KEY_METAL_BRACES);
                orthoResult.add(KEY_INVISALIGN);
                return orthoResult;
            }

            orthoResult.add(KEY_METAL_BRACES);
            orthoResult.add(KEY_INVISALIGN);
            return orthoResult;
        }

        addScore(scores, KEY_GENERAL_EXAM, lower,
                "kham", "kham rang", "kham tong quat", "kiem tra rang", "tu van",
                "lung lay", "sap rung", "viem nuou");

        List<String> result = buildKeywordsByScore(scores);

        boolean hasSpecific = result.stream().anyMatch(k -> !KEY_GENERAL_EXAM.equals(k));
        if (hasSpecific) {
            result.remove(KEY_GENERAL_EXAM);
        }

        if (result.isEmpty()) {
            result.add(KEY_GENERAL_EXAM);
        }

        if (result.size() > 3) {
            return new ArrayList<>(result.subList(0, 3));
        }
        return result;
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

    private boolean isOrthodonticSymptom(String lower) {
        return containsAny(lower,
                "nieng rang", "chinh nha",
                "sai khop can", "khop can lech", "khop can khong chuan",

                "rang lech", "toi bi rang lech", "rang em bi lech", "rang moc lech",
                "rang moc chen chuc", "chen chuc", "rang em chen chuc", "khap khenh", "rang em bi khap khenh",
                "rang xoay", "rang chong cheo", "rang khong deu", "rang lon xon",
                "thieu cho tren cung ham", "cung ham hep",

                "rang thua", "ke rang thua", "khe thua", "rang co khe ho", "thua rang cua",

                "rang ho", "toi bi ho", "mieng bi ho", "ho rang", "ho ham",
                "rang chia", "rang cua dua ra ngoai", "rang tren chia ra nhieu", "overjet",
                "vau", "bi vau",

                "rang mom", "toi bi mom", "bi mom", "ham duoi dua ra truoc", "khop can nguoc", "underbite",

                "can sau", "khop can sau", "overbite", "can phu qua nhieu",
                "rang tren phu het rang duoi", "rang can vao nuou", "rang can vao vom mieng",

                "can ho", "open bite", "rang truoc khong cham nhau",
                "rang sau khong cham nhau", "can lai van ho", "ngam mieng ma rang khong cham",

                "can cheo", "crossbite", "can cheo truoc", "can cheo sau", "can doi dau",

                "lech duong giua", "duong giua rang bi lech", "lech ham", "lech ham chuc nang", "sai lech tuong quan 2 ham",
                "ham tren nho ra", "ham tren hep", "ham duoi nho", "ham duoi lech", "mat can doi xuong ham",

                "rang moc ngam", "rang moc ket", "rang moc sai vi tri", "rang moc lac cho", "rang mai khong moc len",
                "rang moc chen ra ngoai cung", "rang moc cup vao trong", "rang vinh vien moc lech",

                "rang sua ton tai lau gay lech rang", "mat rang sua som gay xo lech rang",
                "rang bi xo lech sau nho rang", "rang ngay cang xo lech", "rang di chuyen", "xe dich",

                "rang cua khong khep duoc", "moi khong khep kin", "moi khong khep kin do rang ho", "cuoi thay rang chia ra",

                "roi loan khop can do thoi quen xau", "sai khop can do mut tay", "sai khop can do day luoi",
                "ngam ti gia lau", "sai khop can do nghien rang", "lech khop can do chan thuong",

                "thieu rang bam sinh", "thua rang", "rang nanh moc ngam", "rang nanh moc lech",

                "nhai bi lech mot ben", "nhai kho vi rang khong khop", "kho can thuc an", "can do an khong dut",
                "nhai khong deu", "khop can la",

                "muon chinh khop can", "toi muon chinh khop can",
                "muon keo deu rang", "muon sap deu rang", "toi muon rang deu hon", "muon dong khe thua",
                "muon chinh ho", "muon chinh mom", "muon chinh can sau", "muon chinh can ho", "muon chinh can cheo",

                "lam dep rang bang nieng", "muon chinh rang", "muon sua rang", "muon lam deu rang"
        );
    }

    private boolean isSevereOrthodonticCase(String lower) {
        return containsAny(lower,
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

    private boolean isInvisalignPreference(String lower) {
        return containsAny(lower,
                "invisalign", "khay trong", "khay trong suot",
                "nieng trong suot", "nieng rang trong suot", "nieng tham my",
                "it lo", "kin dao", "de thao lap", "de thao ra",
                "giao tiep nhieu", "hay gap khach hang"
        );
    }

    private boolean isMetalPreference(String lower) {
        return containsAny(lower,
                "kim loai", "mac cai", "nieng kim loai",
                "nieng rang kim loai", "mac cai kim loai",
                "mac cai thuong", "nieng rang truyen thong"
        );
    }
}