package com.dentalclinic.service.ai;

import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.ServiceRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ServiceMatcher {

    private static final String GROUP_GENERAL_EXAM = "GENERAL_EXAM";
    private static final String GROUP_SCALING = "SCALING";
    private static final String GROUP_WISDOM_TOOTH = "WISDOM_TOOTH_EXTRACTION";
    private static final String GROUP_WHITENING = "WHITENING";
    private static final String GROUP_FILLING = "FILLING";
    private static final String GROUP_ROOT_CANAL = "ROOT_CANAL";
    private static final String GROUP_IMPLANT = "IMPLANT";
    private static final String GROUP_CERCON_CROWN = "CERCON_CROWN";
    private static final String GROUP_METAL_BRACES = "METAL_BRACES";
    private static final String GROUP_INVISALIGN = "INVISALIGN";
    private static final String GROUP_TOOTH_JEWELRY = "TOOTH_JEWELRY";

    private static final String ORTHO_EXACT_METAL = "ORTHO_EXACT_METAL";
    private static final String ORTHO_EXACT_INVIS = "ORTHO_EXACT_INVIS";
    private static final String ORTHO_GENERAL = "ORTHO_GENERAL";
    private static final String ORTHO_COMPLEX = "ORTHO_COMPLEX";
    private static final String ORTHO_AESTHETIC = "ORTHO_AESTHETIC";

    private final ServiceRepository serviceRepository;

    // Khởi tạo bộ matcher dùng để map keyword AI sang dịch vụ thật trong database
    public ServiceMatcher(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    // Tìm danh sách dịch vụ phù hợp nhất từ keyword AI và câu người dùng
    public List<Services> matchServices(List<String> keywords, String rawMessage) {
        List<Services> activeServices = serviceRepository.findByActiveTrue();
        if (activeServices == null || activeServices.isEmpty()) {
            return List.of();
        }

        String raw = normalize(rawMessage);

        String orthoIntent = classifyOrthodonticIntent(raw, keywords);
        if (orthoIntent != null) {
            List<Services> ortho = routeOrthodonticServices(activeServices, orthoIntent);
            if (!ortho.isEmpty()) {
                return ortho;
            }
        }

        LinkedHashSet<String> orderedGroups = inferOrderedGroups(raw, keywords);

        for (String group : orderedGroups) {
            List<Services> matched = matchSingleGroup(activeServices, group);
            if (!matched.isEmpty()) {
                return matched;
            }
        }

        if (isGeneralExamSymptom(raw)) {
            List<Services> generalExam = fallbackGeneralExam(activeServices);
            if (!generalExam.isEmpty()) {
                return generalExam;
            }
        }

        List<Services> ranked = rankByFreeText(activeServices, raw, keywords);
        if (!ranked.isEmpty()) {
            return ranked;
        }

        return fallbackGeneralExam(activeServices);
    }

    // Suy ra thứ tự ưu tiên các nhóm dịch vụ từ câu người dùng và keyword AI
    private LinkedHashSet<String> inferOrderedGroups(String raw, List<String> keywords) {
        LinkedHashSet<String> ordered = new LinkedHashSet<>();

        String primary = decidePrimaryGroupFromRawOrLlm(raw, keywords);
        if (primary != null) {
            ordered.add(primary);
        }

        if (keywords != null) {
            for (String keyword : keywords) {
                String canonical = canonicalKeyword(keyword);
                if (canonical != null
                        && !GROUP_METAL_BRACES.equals(canonical)
                        && !GROUP_INVISALIGN.equals(canonical)) {
                    ordered.add(canonical);
                }
            }
        }

        Map<String, Integer> scoreByGroup = buildGroupScores(raw, keywords);
        scoreByGroup.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .forEach(ordered::add);

        return ordered;
    }

    // Chấm điểm các nhóm dịch vụ để ưu tiên nhóm phù hợp nhất với triệu chứng
    private Map<String, Integer> buildGroupScores(String raw, List<String> keywords) {
        Map<String, Integer> scores = new LinkedHashMap<>();
        putScore(scores, GROUP_GENERAL_EXAM, isGeneralExamSymptom(raw), 7);
        putScore(scores, GROUP_SCALING, isScalingSymptom(raw), 11);
        putScore(scores, GROUP_WISDOM_TOOTH, isWisdomToothSymptom(raw), 20);
        putScore(scores, GROUP_WHITENING, isWhiteningSymptom(raw), 18);
        putScore(scores, GROUP_FILLING, isFillingSymptom(raw), 18);
        putScore(scores, GROUP_ROOT_CANAL, isRootCanalSymptom(raw), 20);
        putScore(scores, GROUP_IMPLANT, isImplantSymptom(raw), 19);
        putScore(scores, GROUP_CERCON_CROWN, isCerconCrownSymptom(raw), 18);
        putScore(scores, GROUP_TOOTH_JEWELRY, isToothJewelrySymptom(raw), 20);

        if (keywords != null) {
            for (String keyword : keywords) {
                String canonical = canonicalKeyword(keyword);
                if (canonical != null) {
                    scores.put(canonical, scores.getOrDefault(canonical, 0) + 8);
                }
            }
        }

        return scores;
    }

    // Cộng điểm cho một nhóm dịch vụ nếu điều kiện triệu chứng thỏa mãn
    private void putScore(Map<String, Integer> scores, String group, boolean matched, int value) {
        if (matched) {
            scores.put(group, scores.getOrDefault(group, 0) + value);
        }
    }

    // Xác định nhóm dịch vụ chính từ câu người dùng hoặc từ kết quả LLM
    private String decidePrimaryGroupFromRawOrLlm(String raw, List<String> keywords) {
        if (isToothJewelrySymptom(raw)) return GROUP_TOOTH_JEWELRY;
        if (isWisdomToothSymptom(raw)) return GROUP_WISDOM_TOOTH;
        if (isImplantSymptom(raw)) return GROUP_IMPLANT;
        if (isRootCanalSymptom(raw)) return GROUP_ROOT_CANAL;
        if (isCerconCrownSymptom(raw)) return GROUP_CERCON_CROWN;
        if (isFillingSymptom(raw)) return GROUP_FILLING;
        if (isWhiteningSymptom(raw)) return GROUP_WHITENING;
        if (isScalingSymptom(raw)) return GROUP_SCALING;
        if (isGeneralExamSymptom(raw)) return GROUP_GENERAL_EXAM;

        String llmTop = firstSpecificGroup(keywords);
        if (llmTop != null) {
            return llmTop;
        }
        return null;
    }

    // Lấy nhóm dịch vụ đặc hiệu đầu tiên, bỏ qua nhóm khám tổng quát nếu có nhóm rõ hơn
    private String firstSpecificGroup(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        for (String keyword : keywords) {
            String canonical = canonicalKeyword(keyword);
            if (canonical != null && !GROUP_GENERAL_EXAM.equals(canonical)) {
                return canonical;
            }
        }
        return null;
    }

    // Lấy nhóm chỉnh nha đầu tiên nếu keyword có liên quan tới niềng răng
    private String firstOrthodonticGroup(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        for (String keyword : keywords) {
            String canonical = canonicalKeyword(keyword);
            if (GROUP_METAL_BRACES.equals(canonical) || GROUP_INVISALIGN.equals(canonical)) {
                return canonical;
            }
        }
        return null;
    }

    // Phân loại ý định chỉnh nha để quyết định route sang niềng kim loại hay invisalign
    private String classifyOrthodonticIntent(String raw, List<String> keywords) {
        String llmOrtho = firstOrthodonticGroup(keywords);

        if (isExactMetalCase(raw)) return ORTHO_EXACT_METAL;
        if (isExactInvisalignCase(raw)) return ORTHO_EXACT_INVIS;
        if (isComplexOrthodonticCase(raw)) return ORTHO_COMPLEX;
        if (isAestheticOrthodonticCase(raw)) return ORTHO_AESTHETIC;
        if (isGeneralOrthodonticCase(raw)) return ORTHO_GENERAL;

        if (GROUP_METAL_BRACES.equals(llmOrtho)) return ORTHO_EXACT_METAL;
        if (GROUP_INVISALIGN.equals(llmOrtho)) return ORTHO_EXACT_INVIS;

        return null;
    }

    // Điều hướng riêng cho nhóm chỉnh nha để trả ra dịch vụ phù hợp đúng thứ tự ưu tiên
    private List<Services> routeOrthodonticServices(List<Services> activeServices, String orthoIntent) {
        return switch (orthoIntent) {
            case ORTHO_EXACT_METAL -> matchOrthodonticServices(activeServices, false, true);
            case ORTHO_EXACT_INVIS -> matchOrthodonticServices(activeServices, true, true);
            case ORTHO_COMPLEX -> matchOrthodonticServices(activeServices, false, false);
            case ORTHO_AESTHETIC -> matchOrthodonticServices(activeServices, true, false);
            case ORTHO_GENERAL -> matchOrthodonticServices(activeServices, false, false);
            default -> List.of();
        };
    }

    // Xếp hạng dịch vụ theo mức độ khớp tự do giữa câu người dùng, keyword AI và mô tả dịch vụ
    private List<Services> rankByFreeText(List<Services> services, String raw, List<String> keywords) {
        Map<Services, Integer> scores = new LinkedHashMap<>();

        for (Services service : services) {
            String name = normalize(service.getName());
            String desc = normalize(service.getDescription());
            int score = 0;

            if (!raw.isBlank()) {
                for (String token : tokenizeLoose(raw)) {
                    if (token.length() < 2) continue;
                    if (name.contains(token)) score += 2;
                    if (desc.contains(token)) score += 1;
                }
            }

            Set<String> groups = detectServiceGroups(service);
            if (keywords != null) {
                for (String keyword : keywords) {
                    String canonical = canonicalKeyword(keyword);
                    if (canonical != null && groups.contains(canonical)) {
                        score += 10;
                    }
                }
            }

            if (isGeneralExamSymptom(raw) && groups.contains(GROUP_GENERAL_EXAM)) score += 9;
            if (isScalingSymptom(raw) && groups.contains(GROUP_SCALING)) score += 12;
            if (isWisdomToothSymptom(raw) && groups.contains(GROUP_WISDOM_TOOTH)) score += 15;
            if (isWhiteningSymptom(raw) && groups.contains(GROUP_WHITENING)) score += 15;
            if (isFillingSymptom(raw) && groups.contains(GROUP_FILLING)) score += 15;
            if (isRootCanalSymptom(raw) && groups.contains(GROUP_ROOT_CANAL)) score += 16;
            if (isImplantSymptom(raw) && groups.contains(GROUP_IMPLANT)) score += 15;
            if (isCerconCrownSymptom(raw) && groups.contains(GROUP_CERCON_CROWN)) score += 15;
            if (isToothJewelrySymptom(raw) && groups.contains(GROUP_TOOTH_JEWELRY)) score += 16;

            scores.put(service, score);
        }

        return scores.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .filter(e -> e.getValue() > 0)
                .map(Map.Entry::getKey)
                .limit(1)
                .collect(Collectors.toList());
    }

    // Match đúng một dịch vụ đại diện cho một nhóm mục tiêu
    private List<Services> matchSingleGroup(List<Services> services, String targetGroup) {
        if (services == null || services.isEmpty() || targetGroup == null || targetGroup.isBlank()) {
            return List.of();
        }

        return services.stream()
                .filter(Objects::nonNull)
                .filter(s -> detectServiceGroups(s).contains(targetGroup))
                .sorted(Comparator.comparing(Services::getId))
                .limit(1)
                .collect(Collectors.toList());
    }

        // Tự nhận diện một dịch vụ trong DB thuộc những nhóm nào dựa trên tên và mô tả
    private Set<String> detectServiceGroups(Services service) {
        Set<String> groups = new LinkedHashSet<>();
        if (service == null) {
            return groups;
        }

        String name = normalize(service.getName());
        String desc = normalize(service.getDescription());
        String text = (name + " " + desc).trim();

        if (containsAnyLoose(text, "kham tong quat", "kham va tu van tong quat", "tu van tong quat", "kiem tra tong quat")) {
            groups.add(GROUP_GENERAL_EXAM);
        }

        if (containsAnyLoose(text, "lay cao rang", "cao voi", "ve sinh rang mieng")) {
            groups.add(GROUP_SCALING);
        }

        if (containsAnyLoose(text, "nho rang khon", "rang khon", "rang so 8", "tieu phau rang khon")) {
            groups.add(GROUP_WISDOM_TOOTH);
        }

        if (containsAnyLoose(text, "tay trang rang", "lam trang rang", "whitening")) {
            groups.add(GROUP_WHITENING);
        }

        if (containsAnyLoose(text, "tram rang", "tram rang tham my", "composite")) {
            groups.add(GROUP_FILLING);
        }

        if (containsAnyLoose(text,
                "dieu tri tuy", "chua tuy", "lay tuy", "tuy rang",
                "noi nha", "dieu tri noi nha", "endodontic", "root canal")) {
            groups.add(GROUP_ROOT_CANAL);
        }

        if (containsAnyLoose(text, "implant", "cay ghep implant", "trong rang implant")) {
            groups.add(GROUP_IMPLANT);
        }

        if (containsAnyLoose(text, "boc rang su cercon", "cercon", "rang su", "mao su")) {
            groups.add(GROUP_CERCON_CROWN);
        }

        if (containsAnyLoose(text, "nieng rang kim loai", "mac cai kim loai", "nieng rang truyen thong", "mac cai thuong")) {
            groups.add(GROUP_METAL_BRACES);
        }

        if (containsAnyLoose(text, "invisalign", "khay trong suot", "nieng rang trong suot")) {
            groups.add(GROUP_INVISALIGN);
        }

        if (containsAnyLoose(text, "dinh da rang", "gan da rang", "tooth jewelry", "trang suc rang")) {
            groups.add(GROUP_TOOTH_JEWELRY);
        }

        return groups;
    }

    // Fallback về dịch vụ khám tổng quát khi không match được nhóm cụ thể nào
    private List<Services> fallbackGeneralExam(List<Services> services) {
        return services.stream()
                .filter(Objects::nonNull)
                .filter(s -> detectServiceGroups(s).contains(GROUP_GENERAL_EXAM))
                .sorted(Comparator.comparing(Services::getId))
                .limit(1)
                .collect(Collectors.toList());
    }

    // Lấy danh sách dịch vụ chỉnh nha theo thứ tự ưu tiên: invisalign trước hoặc kim loại trước
    private List<Services> matchOrthodonticServices(List<Services> activeServices, boolean invisalignFirst, boolean onlyOne) {
        Services metal = activeServices.stream()
                .filter(s -> detectServiceGroups(s).contains(GROUP_METAL_BRACES))
                .findFirst()
                .orElse(null);

        Services invis = activeServices.stream()
                .filter(s -> detectServiceGroups(s).contains(GROUP_INVISALIGN))
                .findFirst()
                .orElse(null);

        List<Services> result = new ArrayList<>();
        if (invisalignFirst) {
            if (invis != null) result.add(invis);
            if (!onlyOne && metal != null) result.add(metal);
        } else {
            if (metal != null) result.add(metal);
            if (!onlyOne && invis != null) result.add(invis);
        }
        return result;
    }

    // Chuẩn hóa keyword thô thành mã nhóm dịch vụ chuẩn của hệ thống
    private String canonicalKeyword(String rawKeyword) {
        String keyword = normalize(rawKeyword);
        if (keyword.isBlank()) {
            return null;
        }

        if (containsAnyLoose(keyword, "general_exam", "general exam", "kham tong quat", "kham rang", "kiem tra rang", "tu van rang")) {
            return GROUP_GENERAL_EXAM;
        }
        if (containsAnyLoose(keyword, "scaling", "cao voi", "lay cao", "ve sinh rang")) {
            return GROUP_SCALING;
        }
        if (containsAnyLoose(keyword, "wisdom_tooth_extraction", "rang khon", "rang so 8", "nho rang khon")) {
            return GROUP_WISDOM_TOOTH;
        }
        if (containsAnyLoose(keyword, "whitening", "tay trang", "lam trang")) {
            return GROUP_WHITENING;
        }
        if (containsAnyLoose(keyword, "filling", "tram", "sau rang", "lo rang", "me rang")) {
            return GROUP_FILLING;
        }
        if (containsAnyLoose(keyword, "root_canal", "tuy", "viem tuy", "dieu tri tuy", "lay tuy")) {
            return GROUP_ROOT_CANAL;
        }
        if (containsAnyLoose(keyword, "implant", "mat rang", "trong rang", "cay ghep")) {
            return GROUP_IMPLANT;
        }
        if (containsAnyLoose(keyword, "cercon_crown", "cercon", "rang su", "boc su", "mao su")) {
            return GROUP_CERCON_CROWN;
        }
        if (containsAnyLoose(keyword, "nieng rang kim loai", "kim loai", "mac cai", "mac cai kim loai", "truyen thong")) {
            return GROUP_METAL_BRACES;
        }
        if (containsAnyLoose(keyword, "invisalign", "nieng trong suot", "khay trong", "khay trong suot", "khong mac cai")) {
            return GROUP_INVISALIGN;
        }
        if (containsAnyLoose(keyword, "tooth_jewelry", "dinh da", "gan da", "gan charm")) {
            return GROUP_TOOTH_JEWELRY;
        }

        return null;
    }

    // Kiểm tra triệu chứng có nghiêng về khám tổng quát hay không
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

    // Kiểm tra triệu chứng có liên quan tới răng khôn hay không
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

    // Kiểm tra nhu cầu có liên quan tới implant hay không
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

    // Kiểm tra người dùng có nói rõ niềng kim loại hay không
    private boolean isExactMetalCase(String raw) {
        return containsAnyLoose(raw,
                "nieng rang kim loai", "nieng mac cai kim loai", "nieng mac cai thuong",
                "nieng rang truyen thong", "nieng bang mac cai", "chinh nha bang mac cai kim loai",
                "nieng kim loai vi chi phi thap", "nieng kim loai cho rang lech");
    }

    // Kiểm tra người dùng có nói rõ niềng Invisalign hay không
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
                "mom", "rang mom", "can sau", "can ho", "can cheo", "lech ham",
                "sai khop can", "khop can lech", "khop can nguoc", "underbite",
                "overbite", "open bite", "crossbite", "chen chuc nang",
                "rang moc ngam", "rang moc ket", "rang moc sai vi tri nhieu",
                "ham tren va ham duoi lech nhau", "can vao nuou khi ngam mieng");
    }

    // Kiểm tra đây có phải nhu cầu chỉnh nha chung chung, chưa nêu rõ loại niềng hay không
    private boolean isGeneralOrthodonticCase(String raw) {
        return containsAnyLoose(raw,
                "nieng rang", "chinh nha",
                "rang lech", "khap khenh", "chen chuc", "rang khong deu", "rang lon xon",
                "rang xoay", "rang chong len nhau", "rang thua", "khe ho", "khe thua",
                "muon rang deu hon", "keo deu rang", "sap deu rang",
                "chinh khop can", "sai khop can", "can sau", "can ho", "can cheo",
                "ho", "mom", "rang cua chia ra ngoai", "ham duoi dua ra truoc");
    }

    // Kiểm tra text có chứa một trong các từ khóa đơn giản hay không
    private boolean containsAny(String text, String... tokens) {
        String normalized = normalize(text);
        for (String t : tokens) {
            if (normalized.contains(normalize(t))) {
                return true;
            }
        }
        return false;
    }

    // Kiểm tra text có chứa một trong các cụm từ gần đúng sau khi chuẩn hóa hay không
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

    // Tách câu thành các token đơn giản để phục vụ việc so khớp mềm
    private List<String> tokenizeLoose(String input) {
        String normalized = normalize(input).replaceAll("[^a-z0-9\\s]", " ");
        if (normalized.isBlank()) {
            return List.of();
        }

        return Arrays.stream(normalized.split("\\s+"))
                .filter(s -> !s.isBlank())
                .toList();
    }

    // Chuẩn hóa text: bỏ dấu, về chữ thường và loại bỏ nhiễu để match dễ hơn
    private String normalize(String input) {
        if (input == null) return "";
        return Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace('đ', 'd')
                .replace('Đ', 'D')
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}