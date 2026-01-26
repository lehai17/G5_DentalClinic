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

    @GetMapping({"/", "/index", "/home"})
    public String redirectToHomepage() {
        return "redirect:/homepage";
    }

    @GetMapping("/homepage")
    public String showHomepage(Model model) {
        try {
            Long currentUserId = 3L; // User ID

            // 1. Lấy dữ liệu Profile khách hàng
            CustomerProfile profile = profileService.getCurrentCustomerProfile(currentUserId);
            if (profile == null) {
                profile = new CustomerProfile();
                profile.setFullName("Khách hàng");
                model.addAttribute("appointments", new ArrayList<>());
            } else {
                // Get appointments by customer.id (which equals user.id due to @MapsId)
                try {
                    model.addAttribute("appointments", profileService.getCustomerAppointments(profile.getId()));
                } catch (Exception e) {
                    model.addAttribute("appointments", new ArrayList<>());
                }
            }
            model.addAttribute("customer", profile);

            // 2. Lấy danh sách Dịch vụ và Bác sĩ để hiển thị lên giao diện mới
            try {
                model.addAttribute("services", serviceRepo.findAll());
            } catch (Exception e) {
                model.addAttribute("services", new ArrayList<>());
            }

            try {
                model.addAttribute("dentists", dentistRepo.findAll());
            } catch (Exception e) {
                model.addAttribute("dentists", new ArrayList<>());
            }

            try {
                model.addAttribute("blogs", blogRepo.findByIsPublishedTrueOrderByCreatedAtDesc());
            } catch (Exception e) {
                model.addAttribute("blogs", new ArrayList<>());
            }

            return "customer/homepage";
        } catch (Exception e) {
            // Fallback: return homepage with empty data
            model.addAttribute("customer", new CustomerProfile());
            model.addAttribute("appointments", new ArrayList<>());
            model.addAttribute("services", new ArrayList<>());
            model.addAttribute("dentists", new ArrayList<>());
            model.addAttribute("blogs", new ArrayList<>());
            return "customer/homepage";
        }
    }
}