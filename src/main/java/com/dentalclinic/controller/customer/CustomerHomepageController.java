package com.dentalclinic.controller.customer;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerProfileService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.Optional;

@Controller
public class CustomerHomepageController {

    private final CustomerProfileService profileService;
    private final ServiceRepository serviceRepo;
    private final DentistProfileRepository dentistRepo;
    private final BlogRepository blogRepo;

    private final UserRepository userRepository;

    public CustomerHomepageController(CustomerProfileService profileService,
                                      ServiceRepository serviceRepo,
                                      DentistProfileRepository dentistRepo,
                                      BlogRepository blogRepo,
                                      UserRepository userRepository) {
        this.profileService = profileService;
        this.serviceRepo = serviceRepo;
        this.dentistRepo = dentistRepo;
        this.blogRepo = blogRepo;
        this.userRepository = userRepository;
    }

    @GetMapping({"/", "/index", "/home"})
    public String redirectToHomepage() {
        return "redirect:/homepage";
    }

    @GetMapping("/homepage")
    public String showHomepage(@RequestParam(defaultValue = "0") int page, Model model) {

        // ===== 0) Check đăng nhập =====
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAuthenticated = auth != null
                && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);

        model.addAttribute("isAuthenticated", isAuthenticated);

        // ===== 1) Nếu đã login => lấy email và profile =====
        CustomerProfile profile = null;

        if (isAuthenticated) {
            String email = extractEmail(auth);
            model.addAttribute("showUserMenu", true);


            if (email != null) {
                Optional<User> userOpt = userRepository.findByEmail(email);
                if (userOpt.isPresent()) {
                    User user = userOpt.get();
                    profile = profileService.getCurrentCustomerProfile(user.getId()); // <-- nếu method này thực chất lấy theo userId
                }
            }
        }

        if (profile == null) {
            profile = new CustomerProfile();
            profile.setFullName("Khách hàng");
            model.addAttribute("appointments", new ArrayList<>());
        } else {
            model.addAttribute("appointments", profileService.getCustomerAppointments(profile.getId()));
        }

        model.addAttribute("customer", profile);

        // ===== 2) Tạo avatar letter =====
        String fullName = profile.getFullName();
        String avatarLetter = buildAvatarLetter(fullName);
        model.addAttribute("avatarLetter", avatarLetter);
        model.addAttribute("showUserMenu", true);


        // ===== 3) Data services/dentists/blogs như bạn đang làm =====
        model.addAttribute("services", serviceRepo.findAll());
        model.addAttribute("dentists", dentistRepo.findAll());

        Pageable pageable = PageRequest.of(page, 2);
        Page<com.dentalclinic.model.blog.Blog> blogPage =
                blogRepo.findByIsPublishedTrueOrderByCreatedAtDesc(pageable);

        model.addAttribute("blogs", blogPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", blogPage.getTotalPages());

        return "customer/homepage";
    }

    private String extractEmail(Authentication auth) {
        Object principal = auth.getPrincipal();

        // Form login
        if (principal instanceof UserDetails userDetails) {
            return userDetails.getUsername(); // bạn đang dùng email làm username
        }

        // Google OAuth2
        if (principal instanceof OAuth2User oauth2User) {
            return oauth2User.getAttribute("email");
        }

        return null;
    }

    private String buildAvatarLetter(String fullName) {
        if (fullName == null) return "K";
        String s = fullName.trim();
        if (s.isEmpty()) return "K";
        return String.valueOf(Character.toUpperCase(s.charAt(0)));
    }
}
