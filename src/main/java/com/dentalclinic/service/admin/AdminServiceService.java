package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.ServiceDTO;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface AdminServiceService {
    List<ServiceDTO> getAllServices();

    ServiceDTO getServiceById(Long id);

    void addService(ServiceDTO serviceDTO, MultipartFile image);

    void updateService(Long id, ServiceDTO serviceDTO, MultipartFile image);

    void toggleServiceStatus(Long id, boolean status);
}
