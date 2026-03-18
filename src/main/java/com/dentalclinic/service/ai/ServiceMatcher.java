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

    public List<Services> matchServices(List<String> keywords) {
        List<Services> activeServices = serviceRepository.findByActiveTrue();
        if (activeServices == null || activeServices.isEmpty()) {
            return List.of();
        }

        if (keywords == null || keywords.isEmpty()) {
            return fallbackGeneralExam(activeServices);
        }

        Map<Services, Integer> scores = new LinkedHashMap<>();
        for (Services service : activeServices) {
            scores.put(service, 0);
        }

        for (String rawKeyword : keywords) {
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
                .filter(e -> e.getValue() >= topScore - 8)
                .map(Map.Entry::getKey)
                .distinct()
                .limit(resolveLimit(keywords))
                .collect(Collectors.toList());

        return result.isEmpty() ? fallbackGeneralExam(activeServices) : result;
    }

    private int resolveLimit(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 1;
        }

        Set<String> canonical = keywords.stream()
                .map(this::canonicalKeyword)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        if (canonical.contains(GROUP_METAL_BRACES) && canonical.contains(GROUP_INVISALIGN)) {
            return 2;
        }
        return Math.min(3, canonical.size() == 0 ? 1 : canonical.size());
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
                if (containsAny(serviceDesc, "rang khon", "moc ngam", "moc lech")) score += 10;
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
                if (containsAny(serviceName, "kim loai", "mac cai")) score += 25;
                if (containsAny(serviceDesc, "kim loai", "mac cai")) score += 10;
            }
            case GROUP_INVISALIGN -> {
                if (containsAny(serviceName, "invisalign", "trong suot", "khay")) score += 25;
                if (containsAny(serviceDesc, "invisalign", "trong suot", "khay")) score += 10;
            }
            case GROUP_TOOTH_JEWELRY -> {
                if (containsAny(serviceName, "dinh da", "gan da")) score += 25;
                if (containsAny(serviceDesc, "dinh da", "gan da")) score += 10;
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
        if (containsAny(name, "dinh da", "gan da") || containsAny(desc, "dinh da", "gan da")) {
            groups.add(GROUP_TOOTH_JEWELRY);
        }

        return groups;
    }

    private String canonicalKeyword(String rawKeyword) {
        String keyword = normalize(rawKeyword);
        if (keyword.isBlank()) {
            return null;
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
        if (containsAny(keyword, "metal_braces", "kim loai", "mac cai")) {
            return GROUP_METAL_BRACES;
        }
        if (containsAny(keyword, "invisalign", "nieng trong suot", "khay trong")) {
            return GROUP_INVISALIGN;
        }
        if (containsAny(keyword, "tooth_jewelry", "dinh da", "gan da")) {
            return GROUP_TOOTH_JEWELRY;
        }

        if (containsAny(keyword, "nieng", "chinh nha", "ho", "vau", "mom", "khap khenh", "chen chuc")) {
            return GROUP_METAL_BRACES;
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
                .toLowerCase(Locale.ROOT)
                .trim();
    }
}