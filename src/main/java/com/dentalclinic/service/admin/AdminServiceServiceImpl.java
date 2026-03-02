package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.ServiceDTO;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.repository.ServicesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AdminServiceServiceImpl implements AdminServiceService {

    @Autowired
    private ServicesRepository servicesRepository;

    private final String UPLOAD_DIR = "src/main/resources/static/uploads/services/";

    @Override
    public List<ServiceDTO> getAllServices() {
        return servicesRepository.findAll().stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    @Override
    public ServiceDTO getServiceById(Long id) {
        Services service = servicesRepository.findById(id).orElseThrow(() -> new RuntimeException("Service not found"));
        return mapToDTO(service);
    }

    @Override
    public void addService(ServiceDTO serviceDTO, MultipartFile image) {
        Services service = new Services();
        mapToEntity(serviceDTO, service);

        if (image != null && !image.isEmpty()) {
            String imageUrl = saveImage(image);
            service.setImageUrl(imageUrl);
        }

        servicesRepository.save(service);
    }

    @Override
    public void updateService(Long id, ServiceDTO serviceDTO, MultipartFile image) {
        Services service = servicesRepository.findById(id).orElseThrow(() -> new RuntimeException("Service not found"));
        mapToEntity(serviceDTO, service);

        if (image != null && !image.isEmpty()) {
            String imageUrl = saveImage(image);
            service.setImageUrl(imageUrl);
        }

        servicesRepository.save(service);
    }

    @Override
    public void toggleServiceStatus(Long id, boolean status) {
        Services service = servicesRepository.findById(id).orElseThrow(() -> new RuntimeException("Service not found"));
        service.setActive(status);
        servicesRepository.save(service);
    }

    private String saveImage(MultipartFile image) {
        try {
            String originalFileName = image.getOriginalFilename();
            if (originalFileName == null)
                originalFileName = "image.png";
            String fileName = StringUtils.cleanPath(originalFileName);
            fileName = System.currentTimeMillis() + "_" + fileName; // Make unique
            Path uploadPath = Paths.get(UPLOAD_DIR);

            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            try (InputStream inputStream = image.getInputStream()) {
                Path filePath = uploadPath.resolve(fileName);
                Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
                return "/uploads/services/" + fileName; // This URL will be accessible via static resources
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not save image file: " + e.getMessage());
        }
    }

    private ServiceDTO mapToDTO(Services service) {
        ServiceDTO dto = new ServiceDTO();
        dto.setId(service.getId());
        dto.setName(service.getName());
        dto.setDescription(service.getDescription());
        dto.setPrice(service.getPrice());
        dto.setDurationMinutes(service.getDurationMinutes());
        dto.setActive(service.isActive());
        dto.setImageUrl(service.getImageUrl());
        return dto;
    }

    private void mapToEntity(ServiceDTO dto, Services service) {
        service.setName(dto.getName());
        service.setDescription(dto.getDescription());
        service.setPrice(dto.getPrice());
        service.setDurationMinutes(dto.getDurationMinutes());
        // Do not update 'active' blindly here if we are just updating info, but DTO
        // might have it
        service.setActive(dto.isActive());
    }
}
