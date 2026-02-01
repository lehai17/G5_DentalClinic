package com.dentalclinic.controller.customer;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserRepository userRepo;

    public CustomerHomepageController(CustomerProfileService profileService,
                                      ServiceRepository serviceRepo,
                                      DentistProfileRepository dentistRepo,
                                      BlogRepository blogRepo,
                                      UserRepository userRepo) {
        this.profileService = profileService;
        this.serviceRepo = serviceRepo;
        this.dentistRepo = dentistRepo;
        this.blogRepo = blogRepo;
        this.userRepo = userRepo;
    }

    /** Lấy ID khách hàng đang đăng nhập; null nếu chưa đăng nhập hoặc không phải CUSTOMER. */
    private Long getCurrentCustomerId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        if (!AuthorityUtils.authorityListToSet(auth.getAuthorities()).contains("ROLE_CUSTOMER")) {
            return null;
        }
        return userRepo.findByEmail(auth.getName()).map(u -> u.getId()).orElse(null);
    }

    /** /index, /home → trang chủ (riêng "/" do WebConfig redirect /homepage). */
    @GetMapping({"/index", "/home"})
    public String redirectToHomepage() {
        return "redirect:/homepage";
    }

    @GetMapping("/homepage")
    public String showHomepage(@RequestParam(defaultValue = "0") int page, Model model) {
        // Chỉ khi đăng nhập với vai trò CUSTOMER mới có profile và lịch hẹn; khách vô danh chỉ xem thông tin chung
        Long currentCustomerId = getCurrentCustomerId();

        try {
            CustomerProfile profile;
            if (currentCustomerId == null) {
                profile = new CustomerProfile();
                profile.setFullName("Khách");
                model.addAttribute("appointments", new ArrayList<>());
                model.addAttribute("loggedIn", false);
            } else {
                profile = profileService.getCurrentCustomerProfile(currentCustomerId);
                if (profile == null) {
                    profile = new CustomerProfile();
                    profile.setFullName("Khách hàng");
                    model.addAttribute("appointments", new ArrayList<>());
                } else {
                    model.addAttribute("appointments", profileService.getCustomerAppointments(profile.getId()));
                }
                model.addAttribute("loggedIn", true);
            }
            model.addAttribute("customer", profile);

            model.addAttribute("services", serviceRepo.findAll());
            model.addAttribute("dentists", dentistRepo.findAll());

            Pageable pageable = PageRequest.of(page, 2);
            Page<Blog> blogPage = blogRepo.findByIsPublishedTrueOrderByCreatedAtDesc(pageable);
            model.addAttribute("blogs", blogPage.getContent());
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", blogPage.getTotalPages());

            return "customer/homepage";

        } catch (Exception e) {
            model.addAttribute("customer", new CustomerProfile());
            model.addAttribute("appointments", new ArrayList<>());
            model.addAttribute("services", serviceRepo.findAll());
            model.addAttribute("dentists", dentistRepo.findAll());
            model.addAttribute("blogs", new ArrayList<>());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            model.addAttribute("loggedIn", currentCustomerId != null);
            return "customer/homepage";
        }
    }

    /** Trang đặt lịch khám (cùng layout với trang chủ) */
    @GetMapping("/customer/book")
    public String bookingPage(Model model) {
        model.addAttribute("services", serviceRepo.findAll());
        return "customer/booking";
    }

    /** Trang lịch hẹn của tôi (cùng layout với trang chủ). Dùng /my-appointments để tránh trùng GET /customer/appointments (API JSON). */
    @GetMapping("/customer/my-appointments")
    public String appointmentsPage() {
        return "customer/appointments";
    }
}