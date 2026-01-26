package com.dentalclinic.controller.customer;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;

@Controller
public class CustomerHomepageController {

    private final CustomerProfileService profileService;
    private final ServiceRepository serviceRepo;
    private final DentistProfileRepository dentistRepo;
    private final BlogRepository blogRepo;

    public CustomerHomepageController(CustomerProfileService profileService,
                                      ServiceRepository serviceRepo,
                                      DentistProfileRepository dentistRepo, BlogRepository blogRepo) {
        this.profileService = profileService;
        this.serviceRepo = serviceRepo;
        this.dentistRepo = dentistRepo;
        this.blogRepo = blogRepo;
    }

    @GetMapping("/homepage")
    public String showHomepage(Model model) {
        Long currentCustomerId = 3L;

        // 1. Lấy dữ liệu Profile khách hàng
        CustomerProfile profile = profileService.getCurrentCustomerProfile(currentCustomerId);
        if (profile == null) {
            profile = new CustomerProfile();
            profile.setFullName("Khách hàng");
            model.addAttribute("appointments", new ArrayList<>());
        } else {
            model.addAttribute("appointments", profileService.getCustomerAppointments(currentCustomerId));
        }
        model.addAttribute("customer", profile);

        // 2. Lấy danh sách Dịch vụ và Bác sĩ để hiển thị lên giao diện mới
        model.addAttribute("services", serviceRepo.findAll());
        model.addAttribute("dentists", dentistRepo.findAll());

        model.addAttribute("blogs", blogRepo.findByIsPublishedTrueOrderByCreatedAtDesc());

        return "customer/homepage"; // Trả về file src/main/resources/templates/customer/homepage.html
    }
}