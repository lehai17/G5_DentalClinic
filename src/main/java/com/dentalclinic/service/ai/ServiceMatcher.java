package com.dentalclinic.service.ai;

import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.ServiceRepository;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ServiceMatcher {

    private final ServiceRepository serviceRepository;

    public ServiceMatcher(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    public List<Services> matchServices(List<String> keywords) {
        List<Services> activeServices = serviceRepository.findByActiveTrue();
        if (keywords == null || keywords.isEmpty()) {
            return activeServices.stream().limit(1).collect(Collectors.toList());
        }

        Set<Services> matched = new LinkedHashSet<>();

        for (String keyword : keywords) {
            String normalizedKeyword = normalize(keyword);

            for (Services service : activeServices) {
                String serviceName = normalize(service.getName());
                String serviceDesc = normalize(service.getDescription());

                if (serviceName.contains(normalizedKeyword)
                        || normalizedKeyword.contains(serviceName)
                        || (!serviceDesc.isBlank() && serviceDesc.contains(normalizedKeyword))) {
                    matched.add(service);
                    continue;
                }

                if (normalizedKeyword.contains("cao voi") && serviceName.contains("cao")) {
                    matched.add(service);
                } else if (normalizedKeyword.contains("kham") && serviceName.contains("kham")) {
                    matched.add(service);
                } else if (normalizedKeyword.contains("nho rang khon") && serviceName.contains("rang khon")) {
                    matched.add(service);
                } else if (normalizedKeyword.contains("chinh nha") && serviceName.contains("chinh nha")) {
                    matched.add(service);
                } else if (normalizedKeyword.contains("tay trang") && serviceName.contains("tay trang")) {
                    matched.add(service);
                }
            }
        }

        if (matched.isEmpty() && !activeServices.isEmpty()) {
            matched.add(activeServices.get(0));
        }

        return new ArrayList<>(matched);
    }

    private String normalize(String input) {
        if (input == null) return "";
        String noAccent = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return noAccent.toLowerCase(Locale.ROOT).trim();
    }
}