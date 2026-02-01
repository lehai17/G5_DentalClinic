package com.dentalclinic.config;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Optional;

@ControllerAdvice
public class GlobalModelAdvice {

    private final UserRepository userRepository;
    private final CustomerProfileService profileService;

    public GlobalModelAdvice(UserRepository userRepository, CustomerProfileService profileService) {
        this.userRepository = userRepository;
        this.profileService = profileService;
    }

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String p && p.equals("anonymousUser"));
    }

    @ModelAttribute("avatarLetter")
    public String avatarLetter(Authentication authentication) {
        String email = extractEmail(authentication);
        if (email == null) return "U";

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "U";

        User user = userOpt.get();
        CustomerProfile profile = profileService.getCurrentCustomerProfile(user.getId());

        String fullName = (profile != null) ? profile.getFullName() : null;
        if (fullName == null || fullName.trim().isEmpty()) return "U";

        return fullName.trim().substring(0, 1).toUpperCase();
    }

    private String extractEmail(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) return ud.getUsername();
        if (principal instanceof OAuth2User ou) return ou.getAttribute("email");
        return null;
    }
}
