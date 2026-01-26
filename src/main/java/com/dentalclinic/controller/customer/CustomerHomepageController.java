package com.dentalclinic.controller.customer;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

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
    public String showHomepage(@RequestParam(defaultValue = "0") int page, Model model) {
        // Sử dụng một ID giả lập hoặc lấy từ Security nếu có
        Long currentCustomerId = 3L;

        try {
            // 1. Lấy dữ liệu Profile khách hàng
            CustomerProfile profile = profileService.getCurrentCustomerProfile(currentCustomerId);
            if (profile == null) {
                profile = new CustomerProfile();
                profile.setFullName("Khách hàng");
                model.addAttribute("appointments", new ArrayList<>());
            } else {
                model.addAttribute("appointments", profileService.getCustomerAppointments(profile.getId()));
            }
            model.addAttribute("customer", profile);

            // 2. Lấy danh sách Dịch vụ và Bác sĩ
            model.addAttribute("services", serviceRepo.findAll());
            model.addAttribute("dentists", dentistRepo.findAll());

            // 3. Xử lý phân trang Blog (Lấy 2 bài mỗi trang)
            Pageable pageable = PageRequest.of(page, 2);
            Page<Blog> blogPage = blogRepo.findByIsPublishedTrueOrderByCreatedAtDesc(pageable);

            model.addAttribute("blogs", blogPage.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", blogPage.getTotalPages());

            return "customer/homepage";

        } catch (Exception e) {
            // Fallback: Trả về trang chủ với danh sách rỗng nếu có lỗi xảy ra
            model.addAttribute("customer", new CustomerProfile());
            model.addAttribute("appointments", new ArrayList<>());
            model.addAttribute("services", new ArrayList<>());
            model.addAttribute("dentists", new ArrayList<>());
            model.addAttribute("blogs", new ArrayList<>());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            return "customer/homepage";
        }
    }
}