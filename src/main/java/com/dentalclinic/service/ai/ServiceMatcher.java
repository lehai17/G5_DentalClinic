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

        if (keywords == null || keywords.isEmpty()) {
            return 1;
        }

        Set<String> canonical = keywords.stream()
                .map(this::canonicalKeyword)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        return Math.min(3, canonical.isEmpty() ? 1 : canonical.size());
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
        String k = normalize(raw);
        return containsAny(k,
                "nieng rang", "chinh nha",

                "sai khop can", "khop can lech", "khop can khong chuan",
                "toi muon chinh khop can", "muon chinh khop can", "chinh khop can",

                "rang lech", "toi bi rang lech", "rang em bi lech", "rang moc lech",
                "rang moc sai vi tri", "rang toi moc sai vi tri", "rang moc lac cho",
                "rang mai khong moc len", "rang moc ket", "rang moc ngam",

                "chen chuc", "rang moc chen chuc", "rang em chen chuc",
                "khap khenh", "rang em bi khap khenh",
                "rang xoay", "rang chong cheo", "rang khong deu", "rang lon xon",
                "toi muon rang deu hon", "muon rang deu hon", "muon lam deu rang",
                "muon keo deu rang", "muon sap deu rang", "muon chinh rang", "muon sua rang",

                "thieu cho tren cung ham", "cung ham hep",

                "rang thua", "ke rang thua", "khe thua", "thua rang cua", "rang co khe ho",
                "muon dong khe thua",

                "rang ho", "toi bi ho", "bi ho", "mieng bi ho", "ho rang", "ho ham",
                "rang vau", "bi vau", "rang chia", "rang cua dua ra ngoai",
                "rang tren chia ra nhieu", "ham tren nho ra", "overjet",
                "muon chinh ho",

                "rang mom", "toi bi mom", "bi mom", "ham duoi dua ra truoc",
                "khop can nguoc", "underbite", "muon chinh mom",

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
                "sai khop can do day luoi", "ngam ti gia lau", "sai khop can do nghien rang",
                "lech khop can do chan thuong",

                "thieu rang bam sinh", "thua rang", "rang nanh moc ngam", "rang nanh moc lech",

                "nhai bi lech mot ben", "nhai kho vi rang khong khop", "kho can thuc an",
                "can do an khong dut", "nhai khong deu", "khop can la",

                "lam dep rang bang nieng"
        );
    }

    private boolean isSevereOrthodonticKeyword(String raw) {
        String k = normalize(raw);
        return containsAny(k,
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

        // nếu đây chỉ là group keyword nội bộ thì không coi là user preference
        if ("invisalign".equals(k)) {
            return false;
        }

        return containsAny(k,
                "nieng trong suot", "nieng rang trong suot",
                "khay trong", "khay trong suot",
                "nieng tham my", "nieng khong mac cai",
                "it lo", "kin dao", "de thao lap", "de thao ra",
                "giao tiep nhieu", "hay gap khach hang",
                "muon nieng trong suot", "muon nieng rang trong suot",
                "muon nieng invisalign"
        ) || k.contains("invisalign ");
    }

    private boolean isMetalPreferenceKeyword(String raw) {
        String k = normalize(raw);

        // nếu đây chỉ là group keyword nội bộ thì không coi là user preference
        if ("metal braces".equals(k) || "metal_braces".equals(k)) {
            return false;
        }

        return containsAny(k,
                "kim loai", "mac cai",
                "nieng kim loai", "nieng rang kim loai",
                "mac cai kim loai", "mac cai thuong",
                "nieng rang truyen thong", "nieng rang mac cai"
        );
    }
}