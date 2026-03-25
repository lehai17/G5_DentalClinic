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

    private static final String ORTHO_EXACT_METAL = "ORTHO_EXACT_METAL";
    private static final String ORTHO_EXACT_INVIS = "ORTHO_EXACT_INVIS";
    private static final String ORTHO_GENERAL = "ORTHO_GENERAL";
    private static final String ORTHO_COMPLEX = "ORTHO_COMPLEX";
    private static final String ORTHO_AESTHETIC = "ORTHO_AESTHETIC";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ServiceRepository serviceRepository;

    @Value("${ai.openai.api-key}")
    private String apiKey;

    @Value("${ai.openai.url}")
    private String apiUrl;

    @Value("${ai.openai.model}")
    private String model;

    // Khởi tạo service gọi OpenAI và truy vấn danh sách dịch vụ từ database
    public OpenAILLMService(RestTemplate restTemplate, ServiceRepository serviceRepository) {
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.serviceRepository = serviceRepository;
    }

    @Override
    // Gọi OpenAI để phân tích câu người dùng thành dữ liệu có cấu trúc cho AI booking
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
- serviceKeywords chỉ được lấy từ danh sách nhóm dịch vụ hợp lệ bên dưới.
- Nếu mô tả rất rõ triệu chứng hoặc nhu cầu, ưu tiên nhóm chuyên biệt.
- Chỉ dùng GENERAL_EXAM khi mô tả còn chung chung, chưa đủ dữ kiện, hoặc triệu chứng chưa đủ phân biệt.
- Với chỉnh nha, AI cần hiểu Ý ĐỊNH và MỨC ĐỘ, không chỉ bám một từ khóa đơn lẻ.

Danh sách nhóm dịch vụ hợp lệ:
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

Catalog dịch vụ hiện có:
%s

Quy tắc suy luận chính:

1) Khám tổng quát:
- Chỉ trả GENERAL_EXAM khi khách nói chung chung như:
  khám răng, kiểm tra răng, tư vấn răng, khám nha khoa, khám định kỳ
  hoặc có triệu chứng mơ hồ như đau răng chưa rõ nguyên nhân, ê buốt nhẹ/chung chung,
  viêm nướu, sưng nướu, chảy máu chân răng, hôi miệng, lung lay nhưng chưa rõ cần dịch vụ gì.

2) Nhổ răng khôn:
- đau răng khôn, đau răng số 8, mọc lệch, mọc ngầm, sưng lợi trùm, đau cuối hàm, đau góc hàm, há miệng đau.

3) Tẩy trắng:
- răng ố vàng, xỉn màu, ngả màu, muốn trắng sáng hơn, whitening, làm trắng răng.

4) Trám răng:
- sâu răng nhẹ/vừa, lỗ sâu nhỏ, lỗ nhỏ trên răng, mẻ nhẹ, sứt nhẹ, thủng nhỏ, muốn trám.

5) Điều trị tủy:
- đau dữ dội, đau tự phát, đau về đêm, đau đến mất ngủ, ê buốt kéo dài nhiều ngày,
  đau sâu trong răng, gõ đau, viêm tủy, áp xe, sưng mủ, chết tủy.

6) Implant:
- mất răng, rụng một chiếc răng, gãy răng mất chân, mất chân răng, muốn trồng răng,
  muốn cấy implant, cấy ghép răng, phục hồi chỗ mất răng.
- Nếu chỉ nói lung lay/sắp rụng, chưa chắc đã mất răng -> ưu tiên GENERAL_EXAM.

7) Bọc sứ Cercon:
- muốn bọc sứ, làm răng sứ, mão sứ, phục hình răng sứ,
  răng vỡ lớn, bể lớn, hư nặng, yếu sau chữa tủy, muốn bọc lại sau điều trị tủy.

8) Đính đá:
- đính đá răng, gắn đá răng, gắn charm, tooth jewelry, trang trí răng bằng đá.

9) Chỉnh nha / niềng:
- Nếu nói rõ niềng kim loại / mắc cài / mắc cài kim loại / truyền thống -> ["METAL_BRACES"]
- Nếu nói rõ Invisalign / niềng trong suốt / khay trong / khay trong suốt / không mắc cài -> ["INVISALIGN"]
- Nếu chỉ mô tả triệu chứng chỉnh nha như răng lệch, chen chúc, khấp khểnh, thưa, hô, móm, cắn sâu, cắn hở, cắn chéo, sai khớp cắn -> trả cả 2 nhóm.
- Nếu ca nặng/phức tạp như móm, cắn sâu, cắn hở, cắn chéo, lệch hàm, mọc ngầm/mọc kẹt, chen chúc nặng -> xếp METAL_BRACES trước INVISALIGN.
- Nếu ưu tiên thẩm mỹ, giao tiếp nhiều, hay gặp khách hàng, không muốn lộ, khó bị phát hiện, tháo lắp, kín đáo -> xếp INVISALIGN trước METAL_BRACES.

10) Nếu câu có thể hợp nhiều nhóm, trả tối đa 3 serviceKeywords, sắp theo mức độ phù hợp giảm dần.

11) preferredDate:
- “mai”, “ngày kia”, “sau 3 ngày”, “tuần sau”, “cuối tuần” -> yyyy-MM-dd

12) preferredTime:
- “14 giờ”, “2 giờ chiều”, “14:30” -> HH:mm

13) timePreference chỉ nhận: morning, afternoon, evening, any
14) urgency chỉ nhận: low, medium, high

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
            body.put("temperature", 0.1);

            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of(
                    "role", "system",
                    "content", "Bạn là trợ lý AI đặt lịch nha khoa, chuyên suy luận nhu cầu dịch vụ từ mô tả tự nhiên của khách hàng."
            ));
            messages.add(Map.of("role", "user", "content", prompt));
            body.put("messages", messages);

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

    // Tạo phần prompt mô tả danh mục dịch vụ hiện có để AI suy luận chính xác hơn
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

    // Làm sạch text đầu vào để tránh null và loại bỏ khoảng trắng thừa
    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    // Tách phần nội dung JSON mà mô hình trả về từ response của OpenAI
    private String extractContent(String rawResponse) throws Exception {
        JsonNode root = objectMapper.readTree(rawResponse);
        JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
        if (contentNode.isMissingNode() || contentNode.isNull()) {
            throw new IllegalStateException("LLM response content is empty");
        }
        return contentNode.asText();
    }

    // Chuẩn hóa toàn bộ dữ liệu AI trả về: ngày, giờ, buổi, độ khẩn cấp và nhóm dịch vụ
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

    // Chuẩn hóa ngày mong muốn, ưu tiên lấy từ AI rồi fallback sang phân tích câu người dùng
    private String safeDate(String preferredDate, String originalMessage) {
        if (preferredDate != null && preferredDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return preferredDate;
        }
        return resolveRelativeDate(normalize(originalMessage));
    }

    // Chuẩn hóa giờ mong muốn, ưu tiên lấy từ AI rồi fallback sang phân tích câu người dùng
    private String safeTime(String preferredTime, String originalMessage) {
        if (preferredTime != null && preferredTime.matches("([01]\\d|2[0-3]):[0-5]\\d")) {
            return preferredTime;
        }
        return resolvePreferredTime(normalize(originalMessage));
    }

    // Chuẩn hóa buổi mong muốn về các giá trị hợp lệ: morning, afternoon, evening hoặc any
    private String safeTimePreference(String timePreference, String originalMessage) {
        if (timePreference != null) {
            String value = timePreference.trim().toLowerCase(Locale.ROOT);
            if (value.equals("morning") || value.equals("afternoon") || value.equals("evening") || value.equals("any")) {
                return value;
            }
        }
        return inferTimePreference(normalize(originalMessage));
    }

    // Chuẩn hóa mức độ khẩn cấp của nhu cầu: low, medium hoặc high
    private String safeUrgency(String urgency, String originalMessage) {
        if (urgency != null) {
            String value = urgency.trim().toLowerCase(Locale.ROOT);
            if (value.equals("low") || value.equals("medium") || value.equals("high")) {
                return value;
            }
        }

        String lower = normalize(originalMessage);
        if (containsAnyLoose(lower,
                "rat dau", "dau du doi", "sung to", "chay mau nhieu", "khan cap", "cap cuu",
                "mat ngu vi dau", "dau ve dem", "dau khong ngu duoc")) {
            return "high";
        }
        if (containsAnyLoose(lower, "dau rang", "e buot", "nhuc", "kho chiu", "viem")) {
            return "medium";
        }
        return "low";
    }

    // Làm sạch danh sách keyword dịch vụ AI trả về: bỏ trùng, chuẩn hóa tên và giới hạn số lượng
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

    // Tinh chỉnh lại keyword dịch vụ dựa trên triệu chứng thực tế trong câu người dùng
    private List<String> refineKeywordsBySymptoms(List<String> llmKeywords, String originalMessage) {
        String raw = normalize(originalMessage);

        String orthoIntent = classifyOrthodonticIntent(raw, llmKeywords);
        if (orthoIntent != null) {
            return switch (orthoIntent) {
                case ORTHO_EXACT_METAL -> List.of(KEY_METAL_BRACES);
                case ORTHO_EXACT_INVIS -> List.of(KEY_INVISALIGN);
                case ORTHO_COMPLEX -> List.of(KEY_METAL_BRACES, KEY_INVISALIGN);
                case ORTHO_AESTHETIC -> List.of(KEY_INVISALIGN, KEY_METAL_BRACES);
                case ORTHO_GENERAL -> List.of(KEY_METAL_BRACES, KEY_INVISALIGN);
                default -> List.of(KEY_GENERAL_EXAM);
            };
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

    // Fallback rule-based khi OpenAI lỗi để hệ thống vẫn có thể suy ra gợi ý cơ bản
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

    // Phân tích các cụm ngày tương đối như mai, ngày kia, tuần sau thành ngày cụ thể
    private String resolveRelativeDate(String lower) {
        LocalDate today = LocalDate.now();

        if (lower == null || lower.isBlank()) {
            return "";
        }

        if (lower.contains("hom nay")) return today.toString();
        if (lower.contains("ngay kia")) return today.plusDays(2).toString();
        if (lower.contains("ngay mai") || lower.matches(".*\\bmai\\b.*")) return today.plusDays(1).toString();

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

    // Phân tích giờ mong muốn từ câu tự nhiên như 8h, 14:30, chiều 2 giờ
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

    // Suy ra buổi mong muốn từ câu người dùng nếu không có giờ cụ thể
    private String inferTimePreference(String lower) {
        if (lower == null || lower.isBlank()) {
            return "any";
        }

        if (containsAnyLoose(lower, "buoi sang", "sang")) return "morning";
        if (containsAnyLoose(lower, "buoi chieu", "chieu")) return "afternoon";
        if (containsAnyLoose(lower, "buoi toi", "toi")) return "evening";
        return "any";
    }

    // Xác định keyword dịch vụ chính dựa trên câu người dùng và kết quả LLM
    private String decidePrimaryServiceKeyword(String raw, List<String> llmKeywords) {
        if (isToothJewelrySymptom(raw)) return KEY_TOOTH_JEWELRY;
        if (isWisdomToothSymptom(raw)) return KEY_WISDOM_TOOTH;
        if (isImplantSymptom(raw)) return KEY_IMPLANT;
        if (isRootCanalSymptom(raw)) return KEY_ROOT_CANAL;
        if (isCerconCrownSymptom(raw)) return KEY_CERCON_CROWN;
        if (isFillingSymptom(raw)) return KEY_FILLING;
        if (isWhiteningSymptom(raw)) return KEY_WHITENING;
        if (isScalingSymptom(raw)) return KEY_SCALING;
        if (isGeneralExamSymptom(raw)) return KEY_GENERAL_EXAM;

        String llmTop = firstSpecificKeyword(llmKeywords);
        if (llmTop != null) {
            return llmTop;
        }
        return null;
    }

    // Lấy keyword dịch vụ đầu tiên có tính đặc hiệu cao, bỏ qua nhóm khám tổng quát nếu có nhóm rõ hơn
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

    // Lấy keyword chỉnh nha đầu tiên nếu người dùng đang hỏi về niềng răng
    private String firstOrthodonticKeyword(List<String> llmKeywords) {
        if (llmKeywords == null || llmKeywords.isEmpty()) {
            return null;
        }

        for (String key : llmKeywords) {
            String canonical = canonicalKeyword(key);
            if (KEY_METAL_BRACES.equals(canonical) || KEY_INVISALIGN.equals(canonical)) {
                return canonical;
            }
        }
        return null;
    }

    // Phân loại ý định chỉnh nha để tách giữa niềng kim loại, invisalign, ca phức tạp hay thẩm mỹ
    private String classifyOrthodonticIntent(String raw, List<String> llmKeywords) {
        String llmOrtho = firstOrthodonticKeyword(llmKeywords);

        if (isExactMetalCase(raw)) return ORTHO_EXACT_METAL;
        if (isExactInvisalignCase(raw)) return ORTHO_EXACT_INVIS;
        if (isComplexOrthodonticCase(raw)) return ORTHO_COMPLEX;
        if (isAestheticOrthodonticCase(raw)) return ORTHO_AESTHETIC;
        if (isGeneralOrthodonticCase(raw)) return ORTHO_GENERAL;

        if (KEY_METAL_BRACES.equals(llmOrtho)) return ORTHO_EXACT_METAL;
        if (KEY_INVISALIGN.equals(llmOrtho)) return ORTHO_EXACT_INVIS;

        return null;
    }

    // Chuẩn hóa keyword thô về tên nhóm dịch vụ chuẩn của hệ thống
    private String canonicalKeyword(String rawKeyword) {
        String keyword = normalize(rawKeyword);
        if (keyword.isBlank()) {
            return null;
        }

        if (containsAnyLoose(keyword, "general_exam", "general exam", "kham tong quat", "kham rang", "kiem tra rang", "tu van rang")) {
            return KEY_GENERAL_EXAM;
        }
        if (containsAnyLoose(keyword, "scaling", "cao voi", "cao rang", "lay cao", "ve sinh rang")) {
            return KEY_SCALING;
        }
        if (containsAnyLoose(keyword, "wisdom_tooth_extraction", "nho rang khon", "rang khon", "rang so 8")) {
            return KEY_WISDOM_TOOTH;
        }
        if (containsAnyLoose(keyword, "whitening", "tay trang", "lam trang", "trang rang")) {
            return KEY_WHITENING;
        }
        if (containsAnyLoose(keyword, "filling", "tram rang", "tram", "sau rang", "lo rang", "me rang")) {
            return KEY_FILLING;
        }
        if (containsAnyLoose(keyword, "root_canal", "tuy", "dieu tri tuy", "viem tuy", "lay tuy")) {
            return KEY_ROOT_CANAL;
        }
        if (containsAnyLoose(keyword, "implant", "cay ghep", "trong rang", "mat rang", "cay implant")) {
            return KEY_IMPLANT;
        }
        if (containsAnyLoose(keyword, "cercon_crown", "cercon", "boc su", "rang su", "mao su", "phuc hinh su")) {
            return KEY_CERCON_CROWN;
        }
        if (containsAnyLoose(keyword, "nieng rang kim loai", "kim loai", "mac cai", "mac cai kim loai", "truyen thong")) {
            return KEY_METAL_BRACES;
        }
        if (containsAnyLoose(keyword, "invisalign", "nieng trong suot", "khay trong", "khay trong suot", "khong mac cai")) {
            return KEY_INVISALIGN;
        }
        if (containsAnyLoose(keyword, "tooth_jewelry", "dinh da", "gan da", "gan charm", "da rang")) {
            return KEY_TOOTH_JEWELRY;
        }
        return null;
    }

    // Kiểm tra triệu chứng có nghiêng về nhu cầu khám tổng quát hay không
    private boolean isGeneralExamSymptom(String raw) {
        return containsAnyLoose(raw,
                "kham rang", "kham tong quat", "kiem tra rang", "kiem tra rang mieng", "tu van rang",
                "di kham nha khoa", "kham dinh ky", "kiem tra tinh trang rang",
                "dau rang ma chua biet bi gi", "e buot rang", "nhay cam rang",
                "viem nuou", "sung nuou", "chay mau chan rang", "hoi mieng",
                "rang hoi lung lay", "rang co van de muon di kham", "kho chiu o rang nhung chua ro"
        );
    }

    // Kiểm tra triệu chứng có nghiêng về cạo vôi, lấy cao răng hay không
    private boolean isScalingSymptom(String raw) {
        return containsAnyLoose(raw,
                "cao voi", "cao voi rang", "lay cao rang", "cao rang",
                "ve sinh rang mieng", "ve sinh rang", "voi rang", "cao mang bam");
    }

    // Kiểm tra triệu chứng có liên quan tới răng khôn hay nhổ răng khôn hay không
    private boolean isWisdomToothSymptom(String raw) {
        return containsAnyLoose(raw,
                "rang khon", "rang so 8", "nho rang khon", "nho rang so 8",
                "dau rang khon", "rang khon moc lech", "rang khon moc ngam",
                "sung loi trum", "dau cuoi ham", "dau goc ham",
                "ha mieng bi dau vi rang khon", "viem quanh rang khon", "phia trong cung cua ham");
    }

    // Kiểm tra nhu cầu có liên quan tới tẩy trắng răng hay không
    private boolean isWhiteningSymptom(String raw) {
        return containsAnyLoose(raw,
                "tay trang rang", "lam trang rang", "whitening",
                "rang o vang", "rang xi mau", "rang vang", "rang nga mau",
                "rang khong con trang", "muon rang trang hon", "trang sang hon", "cai thien mau rang");
    }

    // Kiểm tra nhu cầu có liên quan tới trám răng hay không
    private boolean isFillingSymptom(String raw) {
        return containsAnyLoose(raw,
                "tram rang", "tram tham my", "sau rang", "lo sau", "lo nho tren rang",
                "rang bi lo", "rang bi thung", "me nhe", "sut nhe", "vo nho",
                "den mat nhai", "thuc an mac vao lo", "tram cho sau rang");
    }

    // Kiểm tra triệu chứng có nghiêng về điều trị tủy hay không
    private boolean isRootCanalSymptom(String raw) {
        return containsAnyLoose(raw,
                "dau rang du doi", "dau rang ve dem", "mat ngu vi dau rang",
                "e buot keo dai", "viem tuy", "chua tuy", "lay tuy",
                "dieu tri tuy", "tuy rang", "tuy co van de", "tuy rang co van de",
                "rang bi van de tuy", "co van de ve tuy", "viem tuy rang",
                "chay mau tuy", "chay mau tuy rang",
                "noi nha", "dieu tri noi nha", "endodontic",
                "dau sau ben trong rang", "dau rang theo con giat", "go vao rang thay dau",
                "ap xe rang", "sung mu", "dau rang tu phat", "chet tuy", "dau buot lien tuc");
    }

    // Kiểm tra nhu cầu có liên quan tới trồng răng implant hay không
    private boolean isImplantSymptom(String raw) {
        return containsAnyLoose(raw,
                "mat rang", "muon trong rang", "cay implant", "cay ghep implant", "lam implant",
                "gay rang mat chan", "mat chan rang", "rung mot chiec rang",
                "phuc hoi cho mat rang", "mat rang lau nam", "nho rang roi muon trong lai",
                "trong lai rang da mat", "mat mot rang cua", "mat rang ham",
                "rang co dinh sau khi mat rang");
    }

    // Kiểm tra nhu cầu có liên quan tới bọc răng sứ Cercon hay không
    private boolean isCerconCrownSymptom(String raw) {
        return containsAnyLoose(raw,
                "boc rang su", "lam rang su", "boc su cercon", "phuc hinh rang su",
                "rang vo lon", "rang be lon", "me nhieu", "rang hu nang",
                "rang yeu sau khi chua tuy", "boc lai rang sau dieu tri tuy",
                "rang cua xau muon boc su", "cai thien tham my bang su",
                "nhiem mau nang", "lam mao su", "phuc hinh bang cercon");
    }

    // Kiểm tra nhu cầu có liên quan tới đính đá răng hay không
    private boolean isToothJewelrySymptom(String raw) {
        return containsAnyLoose(raw,
                "dinh da rang", "gan da rang", "lam dep rang bang da", "gan charm len rang",
                "dinh hat da len rang", "gan da trang tri len rang",
                "trang tri rang bang da", "gan mot vien da len rang cua", "tooth jewelry");
    }

    // Kiểm tra người dùng có nói rõ đích danh niềng kim loại hay không
    private boolean isExactMetalCase(String raw) {
        return containsAnyLoose(raw,
                "nieng rang kim loai", "nieng mac cai kim loai", "nieng mac cai thuong",
                "nieng rang truyen thong", "nieng bang mac cai", "chinh nha bang mac cai kim loai",
                "nieng kim loai vi chi phi thap", "nieng kim loai cho rang lech");
    }

    // Kiểm tra người dùng có nói rõ đích danh niềng Invisalign hay không
    private boolean isExactInvisalignCase(String raw) {
        return containsAnyLoose(raw,
                "nieng invisalign", "nieng rang invisalign", "nieng rang trong suot",
                "nieng khay trong suot", "nieng bang khay trong", "chinh nha bang invisalign",
                "nieng tham my", "nieng kin dao", "nieng it bi phat hien",
                "nieng kho bi nhan ra", "nieng de thao lap", "nieng khong mac cai",
                "nieng rang nhung khong muon lo");
    }

    // Kiểm tra người dùng có ưu tiên tính thẩm mỹ khi chỉnh nha hay không
    private boolean isAestheticOrthodonticCase(String raw) {
        return containsAnyLoose(raw,
                "hay gap khach hang", "giao tiep nhieu", "muon tham my hon", "khong muon lo",
                "it bi phat hien", "kho bi nhan ra", "kin dao", "de thao lap", "de giao tiep",
                "tu tin hon khi giao tiep", "gap khach hang khong bi lo");
    }

    // Kiểm tra đây có phải ca chỉnh nha phức tạp cần ưu tiên niềng truyền thống hay không
    private boolean isComplexOrthodonticCase(String raw) {
        return containsAnyLoose(raw,
                "mom", "rang mom",
                "rang ho", "ho rang", "ho ham",
                "rang vau", "vau", "bi vau",
                "rang cua dua ra ngoai", "rang tren dua ra ngoai", "ham tren dua ra",
                "overjet", "muon chinh ho", "muon chinh vau",

                "can sau", "can ho", "can cheo", "lech ham",
                "sai khop can", "khop can lech", "khop can nguoc", "underbite",
                "overbite", "open bite", "crossbite", "chen chuc nang",
                "rang moc ngam", "rang moc ket", "rang moc sai vi tri nhieu",
                "ham tren va ham duoi lech nhau", "can vao nuou khi ngam mieng");
    }

    // Kiểm tra đây có phải nhu cầu chỉnh nha chung chung, chưa chỉ rõ loại niềng hay không
    private boolean isGeneralOrthodonticCase(String raw) {
        return containsAnyLoose(raw,
                "nieng rang", "chinh nha",
                "rang lech", "khap khenh", "chen chuc", "rang khong deu", "rang lon xon",
                "rang xoay", "rang chong len nhau", "rang thua", "khe ho", "khe thua",
                "muon rang deu hon", "keo deu rang", "sap deu rang",

                "rang ho", "ho rang", "ho ham",
                "rang vau", "vau", "bi vau",
                "rang cua dua ra ngoai", "rang tren dua ra ngoai", "ham tren dua ra",
                "muon chinh ho", "muon chinh vau", "overjet",

                "chinh khop can", "sai khop can", "can sau", "can ho", "can cheo",
                "mom", "rang mom", "ham duoi dua ra truoc");
    }

    // Kiểm tra text có chứa một trong các từ khóa đơn giản được truyền vào hay không
    private boolean containsAny(String text, String... tokens) {
        String normalizedText = normalize(text);
        for (String token : tokens) {
            if (normalizedText.contains(normalize(token))) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra text có chứa một trong các cụm từ gần đúng sau khi đã chuẩn hóa hay không
    private boolean containsAnyLoose(String text, String... phrases) {
        for (String phrase : phrases) {
            if (containsPhraseLoose(text, phrase)) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra một cụm từ có xuất hiện gần đúng trong câu sau khi chuẩn hóa hay không
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

    // Tách câu thành các token đơn giản để phục vụ so khớp mềm
    private List<String> tokenizeLoose(String input) {
        String normalized = normalize(input).replaceAll("[^a-z0-9\\s]", " ");
        if (normalized.isBlank()) {
            return List.of();
        }

        return Arrays.stream(normalized.split("\\s+"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    // Chuẩn hóa text: bỏ dấu, về chữ thường và loại bỏ ký tự không cần thiết
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
}