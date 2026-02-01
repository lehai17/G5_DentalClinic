package com.dentalclinic.controller.customer;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Optional;

@Controller
public class ProfileController {

    private final UserRepository userRepository;
    private final CustomerProfileService profileService;

    public ProfileController(UserRepository userRepository, CustomerProfileService profileService) {
        this.userRepository = userRepository;
        this.profileService = profileService;
    }

    @GetMapping("/profile")
    public String profile(Model model, Authentication authentication) {

        String email = extractEmail(authentication);
        if (email == null) return "redirect:/login";

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/login";

        User user = userOpt.get();

        // lấy customer profile theo userId (tuỳ service bạn đang viết)
        CustomerProfile profile = profileService.getCurrentCustomerProfile(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("profile", profile);
        model.addAttribute("showUserMenu", true);

        return "customer/profile";
    }

    private String extractEmail(Authentication auth) {
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) return ud.getUsername();
        if (principal instanceof OAuth2User ou) return ou.getAttribute("email");
        return null;
    }
}
