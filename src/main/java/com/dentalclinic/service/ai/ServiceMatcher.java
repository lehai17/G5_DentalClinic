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

    private final ServiceRepository serviceRepository;

    public ServiceMatcher(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public List<Services> matchServices(List<String> keywords, String rawMessage) {
        List<Services> activeServices = serviceRepository.findByActiveTrue();
        if (activeServices == null || activeServices.isEmpty()) {
            return List.of();
        }

        String normalizedRaw = normalize(rawMessage);

        List<String> allInputs = new ArrayList<>();
        if (rawMessage != null && !rawMessage.isBlank()) {
            allInputs.add(rawMessage);
        }
        if (keywords != null) {
            allInputs.addAll(keywords);
        }

        if (allInputs.isEmpty()) {
            return fallbackGeneralExam(activeServices);
        }

        // Chỉ dùng RAW MESSAGE để xác định user có nói rõ muốn Invisalign / kim loại hay không
        boolean rawOrthodonticSymptom = isOrthodonticKeyword(normalizedRaw);
        boolean rawPreferInvisalign = isInvisalignPreferenceKeyword(normalizedRaw);
        boolean rawPreferMetal = isMetalPreferenceKeyword(normalizedRaw);
        boolean rawSevereOrthodontic = isSevereOrthodonticKeyword(normalizedRaw);

        // Nếu user có biểu hiện chỉnh nha thì ưu tiên xử lý cứng ở đây,

        // không để rơi xuống scoring chung nữa
        if (rawOrthodonticSymptom) {
            // user nói rõ 1 phương pháp
            if (rawPreferMetal && !rawPreferInvisalign) {
                List<Services> result = matchOrthodonticServices(activeServices, false, true);
                if (!result.isEmpty()) {
                    return result;
                }
            }

            if (rawPreferInvisalign && !rawPreferMetal) {
                List<Services> result = matchOrthodonticServices(activeServices, true, true);
                if (!result.isEmpty()) {
                    return result;
                }
            }

            // user chỉ mô tả triệu chứng -> luôn trả 2 lựa chọn
            boolean invisalignFirst = rawPreferInvisalign && !rawSevereOrthodontic;
            if (rawPreferMetal || rawSevereOrthodontic) {
                invisalignFirst = false;
            }

            List<Services> orthoServices = matchOrthodonticServices(activeServices, invisalignFirst, false);
            if (!orthoServices.isEmpty()) {
                return orthoServices;
            }
        }

        String primaryNonOrthoGroup = decidePrimaryGroupFromRawOrLlm(normalizedRaw, keywords);
        if (primaryNonOrthoGroup != null) {
            List<Services> result = matchSingleGroup(activeServices, primaryNonOrthoGroup);
            if (!result.isEmpty()) {
                return result;
            }
        }

        Map<Services, Integer> scores = new LinkedHashMap<>();
        for (Services service : activeServices) {
            scores.put(service, 0);
        }

        for (String rawKeyword : allInputs) {
            String canonicalKeyword = canonicalKeyword(rawKeyword);
            String normalizedKeyword = normalize(rawKeyword);

            for (Services service : activeServices) {
                int score = scores.get(service);
                Set<String> groups = detectServiceGroups(service);

                if (canonicalKeyword != null && groups.contains(canonicalKeyword)) {
                    score += 100;
                }

                score += scoreByTextSimilarity(service, normalizedKeyword, canonicalKeyword);
                scores.put(service, score);
            }
        }

        List<Map.Entry<Services, Integer>> ranked = scores.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .toList();

        if (ranked.isEmpty() || ranked.get(0).getValue() <= 0) {
            return fallbackGeneralExam(activeServices);
        }

        int topScore = ranked.get(0).getValue();
        List<Services> result = ranked.stream()
                .filter(e -> e.getValue() > 0)
                .filter(e -> e.getValue() >= topScore - 2)
                .map(Map.Entry::getKey)
                .distinct()
                .limit(resolveLimit(keywords, rawMessage))
                .collect(Collectors.toList());

        return result.isEmpty() ? fallbackGeneralExam(activeServices) : result;
    }

    private int resolveLimit(List<String> keywords, String rawMessage) {
        String normalizedRaw = normalize(rawMessage);

        boolean rawOrthodonticSymptom = isOrthodonticKeyword(normalizedRaw);
        boolean rawPreferInvisalign = isInvisalignPreferenceKeyword(normalizedRaw);
        boolean rawPreferMetal = isMetalPreferenceKeyword(normalizedRaw);

        if (rawOrthodonticSymptom) {
            if ((rawPreferInvisalign && !rawPreferMetal) || (rawPreferMetal && !rawPreferInvisalign)) {
                return 1;
            }
            return 2;
        }

        return 1;
    }


    private int scoreByTextSimilarity(Services service, String normalizedKeyword, String canonicalKeyword) {
        String serviceName = normalize(service.getName());
        String serviceDesc = normalize(service.getDescription());
        int score = 0;

        if (!normalizedKeyword.isBlank()) {
            if (serviceName.contains(normalizedKeyword)) {
                score += 20;
            }
            if (!serviceDesc.isBlank() && serviceDesc.contains(normalizedKeyword)) {
                score += 8;
            }
        }

        if (canonicalKeyword == null) {
            return score;
        }

        switch (canonicalKeyword) {
            case GROUP_WISDOM_TOOTH -> {
                if (containsAny(serviceName, "rang khon", "rang so 8", "nho rang")) score += 25;
                if (containsAny(serviceDesc, "rang khon", "moc ngam", "moc lech", "nho rang")) score += 10;
            }
            case GROUP_FILLING -> {
                if (containsAny(serviceName, "tram")) score += 25;
                if (containsAny(serviceDesc, "tram", "sau rang", "lo rang", "me rang")) score += 10;
            }
            case GROUP_SCALING -> {
                if (containsAny(serviceName, "cao voi", "lay cao")) score += 25;
                if (containsAny(serviceDesc, "cao voi", "ve sinh rang", "voi rang")) score += 10;
            }
            case GROUP_WHITENING -> {
                if (containsAny(serviceName, "tay trang", "whitening")) score += 25;
                if (containsAny(serviceDesc, "tay trang", "lam trang")) score += 10;
            }
            case GROUP_ROOT_CANAL -> {
                if (containsAny(serviceName, "tuy", "lay tuy", "dieu tri tuy")) score += 25;
                if (containsAny(serviceDesc, "tuy", "viem tuy", "noi nha")) score += 10;
            }
            case GROUP_IMPLANT -> {
                if (containsAny(serviceName, "implant", "trong rang")) score += 25;
                if (containsAny(serviceDesc, "implant", "cay ghep", "mat rang")) score += 10;
            }
            case GROUP_CERCON_CROWN -> {
                if (containsAny(serviceName, "cercon", "rang su", "boc su")) score += 25;
                if (containsAny(serviceDesc, "cercon", "rang su", "phuc hinh")) score += 10;
            }
            case GROUP_METAL_BRACES -> {
                if (containsAny(serviceName, "kim loai", "mac cai", "nieng rang")) score += 25;
                if (containsAny(serviceDesc, "kim loai", "mac cai", "nieng rang")) score += 10;
            }
            case GROUP_INVISALIGN -> {
                if (containsAny(serviceName, "invisalign", "trong suot", "khay")) score += 25;
                if (containsAny(serviceDesc, "invisalign", "trong suot", "khay")) score += 10;
            }
            case GROUP_TOOTH_JEWELRY -> {
                if (containsAny(serviceName, "dinh da", "gan da", "da rang")) score += 25;
                if (containsAny(serviceDesc, "dinh da", "gan da", "da rang")) score += 10;
            }
            case GROUP_GENERAL_EXAM -> {
                if (containsAny(serviceName, "kham", "tu van", "tong quat")) score += 20;
                if (containsAny(serviceDesc, "kham", "tu van", "kiem tra")) score += 8;
            }
            default -> {
            }
        }

        return score;
    }

    private Set<String> detectServiceGroups(Services service) {
        String name = normalize(service.getName());
        String desc = normalize(service.getDescription());

        Set<String> groups = new LinkedHashSet<>();

        if (containsAny(name, "kham", "tong quat", "tu van") || containsAny(desc, "kham", "tu van", "kiem tra")) {
            groups.add(GROUP_GENERAL_EXAM);
        }
        if (containsAny(name, "cao voi", "lay cao") || containsAny(desc, "cao voi", "voi rang", "ve sinh rang")) {
            groups.add(GROUP_SCALING);
        }
        if (containsAny(name, "rang khon", "rang so 8", "nho rang") || containsAny(desc, "rang khon", "moc ngam", "moc lech")) {
            groups.add(GROUP_WISDOM_TOOTH);
        }
        if (containsAny(name, "tay trang", "whitening") || containsAny(desc, "tay trang", "lam trang")) {
            groups.add(GROUP_WHITENING);
        }
        if (containsAny(name, "tram") || containsAny(desc, "tram", "sau rang", "lo rang", "me rang")) {
            groups.add(GROUP_FILLING);
        }
        if (containsAny(name, "tuy", "lay tuy", "dieu tri tuy") || containsAny(desc, "viem tuy", "dieu tri tuy", "noi nha")) {
            groups.add(GROUP_ROOT_CANAL);
        }
        if (containsAny(name, "implant", "trong rang") || containsAny(desc, "implant", "cay ghep", "mat rang")) {
            groups.add(GROUP_IMPLANT);
        }
        if (containsAny(name, "cercon", "rang su", "boc su") || containsAny(desc, "cercon", "rang su", "phuc hinh")) {
            groups.add(GROUP_CERCON_CROWN);
        }
        if (containsAny(name, "kim loai", "mac cai") || containsAny(desc, "kim loai", "mac cai")) {
            groups.add(GROUP_METAL_BRACES);
        }
        if (containsAny(name, "invisalign", "trong suot", "khay") || containsAny(desc, "invisalign", "trong suot", "khay")) {
            groups.add(GROUP_INVISALIGN);
        }
        if (containsAny(name, "dinh da", "gan da", "da rang") || containsAny(desc, "dinh da", "gan da", "da rang")) {
            groups.add(GROUP_TOOTH_JEWELRY);
        }

        return groups;
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

    private String canonicalKeyword(String rawKeyword) {
        String keyword = normalize(rawKeyword);
        if (keyword.isBlank()) {
            return null;
        }

        if (containsAny(keyword, "lam dep rang", "tham my rang")) {
            return GROUP_WHITENING;
        }
        if (containsAny(keyword, "general_exam", "general exam", "kham tong quat", "kham", "tu van")) {
            return GROUP_GENERAL_EXAM;
        }
        if (containsAny(keyword, "scaling", "cao voi", "cao rang", "lay cao", "ve sinh rang")) {
            return GROUP_SCALING;
        }
        if (containsAny(keyword, "wisdom_tooth_extraction", "rang khon", "nho rang khon", "rang so 8")) {
            return GROUP_WISDOM_TOOTH;
        }
        if (containsAny(keyword, "whitening", "tay trang", "lam trang")) {
            return GROUP_WHITENING;
        }
        if (containsAny(keyword, "filling", "tram", "sau rang", "lo rang", "me rang")) {
            return GROUP_FILLING;
        }
        if (containsAny(keyword, "root_canal", "tuy", "viem tuy", "dieu tri tuy")) {
            return GROUP_ROOT_CANAL;
        }
        if (containsAny(keyword, "implant", "mat rang", "trong rang", "cay ghep")) {
            return GROUP_IMPLANT;
        }
        if (containsAny(keyword, "cercon_crown", "cercon", "rang su", "boc su", "phuc hinh")) {
            return GROUP_CERCON_CROWN;
        }
        if (containsAny(keyword,
                "nieng rang kim loai", "kim loai", "mac cai", "mac cai kim loai", "mac cai thuong", "nieng rang thuong")) {
            return GROUP_METAL_BRACES;
        }
        if (containsAny(keyword,
                "invisalign", "nieng trong suot", "khay trong", "khay trong suot", "tham my")) {
            return GROUP_INVISALIGN;
        }
        if (containsAny(keyword, "tooth_jewelry", "dinh da", "gan da")) {
            return GROUP_TOOTH_JEWELRY;
        }

        return null;
    }

    private boolean containsAny(String text, String... tokens) {
        String normalized = normalize(text);
        for (String t : tokens) {
            if (normalized.contains(normalize(t))) {
                return true;
            }
        }
        return false;
    }

    private List<Services> fallbackGeneralExam(List<Services> services) {
        List<Services> generalExam = services.stream()
                .filter(s -> {
                    String name = normalize(s.getName());
                    String desc = normalize(s.getDescription());
                    return containsAny(name, "kham", "tong quat", "tu van")
                            || containsAny(desc, "kham", "tong quat", "tu van", "kiem tra");
                })
                .limit(1)
                .collect(Collectors.toList());

        if (!generalExam.isEmpty()) {
            return generalExam;
        }

        return services.stream().limit(1).collect(Collectors.toList());
    }

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

    private List<Services> matchOrthodonticServices(List<Services> activeServices, boolean invisalignFirst, boolean onlyOne) {
        Services metal = activeServices.stream()
                .filter(s -> {
                    String name = normalize(s.getName());
                    String desc = normalize(s.getDescription());
                    return containsAny(name,
                            "kim loai", "mac cai", "nieng rang kim loai", "mac cai thuong", "nieng rang thuong", "nieng rang truyen thong")
                            || containsAny(desc,
                            "kim loai", "mac cai", "nieng rang kim loai", "mac cai thuong", "nieng rang thuong", "nieng rang truyen thong");
                })
                .findFirst()
                .orElse(null);

        Services invis = activeServices.stream()
                .filter(s -> {
                    String name = normalize(s.getName());
                    String desc = normalize(s.getDescription());
                    return containsAny(name, "invisalign", "trong suot", "khay trong", "khay trong suot")
                            || containsAny(desc, "invisalign", "trong suot", "khay trong", "khay trong suot");
                })
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

    private boolean isOrthodonticKeyword(String raw) {
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

    private boolean isSevereOrthodonticKeyword(String raw) {
        return containsAnyLoose(raw,
                "mom", "khop can nguoc", "underbite",
                "can sau", "khop can sau", "overbite",
                "can ho", "open bite",
                "can cheo", "crossbite",
                "lech ham", "sai khop can", "khop can lech",
                "chen chuc nang", "rang moc ngam", "rang moc ket",
                "rang moc lac cho", "rang nanh moc ngam", "rang nanh moc lech",
                "mat can doi xuong ham"
        );
    }

    private boolean isInvisalignPreferenceKeyword(String raw) {
        String k = normalize(raw);

        if ("invisalign".equals(k)) {
            return false;
        }

        return containsAnyLoose(raw,
                "nieng trong suot", "nieng rang trong suot",
                "khay trong", "khay trong suot",
                "nieng tham my", "tham my hon", "phuong phap tham my", "chinh nha tham my",
                "it lo", "kin dao", "kin dao hon",
                "de thao lap", "de thao ra",
                "giao tiep nhieu", "hay gap khach hang",
                "muon nieng trong suot", "muon nieng rang trong suot",
                "muon nieng invisalign",
                "it bi phat hien", "kho bi phat hien", "kho nhan ra", "khong muon lo"
        ) || k.contains("invisalign ");
    }

    private boolean isMetalPreferenceKeyword(String raw) {
        String k = normalize(raw);

        if ("metal braces".equals(k) || "metal_braces".equals(k)) {
            return false;
        }

        return containsAnyLoose(raw,
                "kim loai", "mac cai",
                "nieng kim loai", "nieng rang kim loai",
                "mac cai kim loai", "mac cai thuong",
                "nieng rang truyen thong", "nieng rang mac cai"
        );
    }

    private String decidePrimaryGroupFromRawOrLlm(String raw, List<String> keywords) {
        if (isToothJewelrySymptom(raw)) {
            return GROUP_TOOTH_JEWELRY;
        }
        if (isWhiteningSymptom(raw)) {
            return GROUP_WHITENING;
        }
        if (isWisdomToothSymptom(raw)) {
            return GROUP_WISDOM_TOOTH;
        }
        if (isImplantSymptom(raw)) {
            return GROUP_IMPLANT;
        }
        if (isRootCanalSymptom(raw)) {
            return GROUP_ROOT_CANAL;
        }
        if (isCerconCrownSymptom(raw)) {
            return GROUP_CERCON_CROWN;
        }
        if (isFillingSymptom(raw)) {
            return GROUP_FILLING;
        }
        if (isScalingSymptom(raw)) {
            return GROUP_SCALING;
        }

        String llmTop = firstSpecificGroup(keywords);
        if (llmTop != null) {
            return llmTop;
        }

        if (isGeneralExamSymptom(raw)) {
            return GROUP_GENERAL_EXAM;
        }

        return null;
    }

    private String firstSpecificGroup(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return null;
        }

        for (String keyword : keywords) {
            String canonical = canonicalKeyword(keyword);
            if (canonical != null && !GROUP_GENERAL_EXAM.equals(canonical)
                    && !GROUP_METAL_BRACES.equals(canonical)
                    && !GROUP_INVISALIGN.equals(canonical)) {
                return canonical;
            }
        }
        return null;
    }

    private List<Services> matchSingleGroup(List<Services> activeServices, String group) {
        return activeServices.stream()
                .filter(s -> detectServiceGroups(s).contains(group))
                .limit(1)
                .collect(Collectors.toList());
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