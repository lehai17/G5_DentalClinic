package com.dentalclinic.config;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import com.dentalclinic.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ControllerAdvice
public class GlobalModelAdvice {
    private static final Logger log = LoggerFactory.getLogger(GlobalModelAdvice.class);

    private final UserRepository userRepository;
    private final CustomerProfileService profileService;
    private final NotificationService notificationService;

    public GlobalModelAdvice(UserRepository userRepository,
                             CustomerProfileService profileService,
                             NotificationService notificationService) {
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.notificationService = notificationService;
    }

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null && authentication.isAuthenticated()
                && !(authentication.getPrincipal() instanceof String p && p.equals("anonymousUser"));
    }

    @ModelAttribute("avatarLetter")
    public String avatarLetter(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return "U";
        }

        String email = extractEmail(authentication);
        if (email == null) return "U";

        try {
            return userRepository.findByEmail(email)
                    .map(user -> profileService.getCurrentCustomerProfile(user.getId()))
                    .map(profile -> profile != null ? profile.getFullName() : null)
                    .filter(name -> name != null && !name.trim().isEmpty())
                    .map(name -> name.trim().substring(0, 1).toUpperCase())
                    .orElse("U");
        } catch (RuntimeException ex) {
            log.warn("Failed to build avatar letter for user: {}", email, ex);
            return "U";
        }
    }

    private String extractEmail(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) return ud.getUsername();
        if (principal instanceof OAuth2User ou) return ou.getAttribute("email");
        return null;
    }

    @ModelAttribute("customerUnreadNotificationCount")
    public long customerUnreadNotificationCount(Authentication authentication) {
        try {
            return resolveCurrentCustomer(authentication)
                    .map(user -> notificationService.countUnread(user.getId()))
                    .orElse(0L);
        } catch (RuntimeException ex) {
            log.warn("Failed to load unread notification count", ex);
            return 0L;
        }
    }

    @ModelAttribute("customerNotificationPreview")
    public List<Notification> customerNotificationPreview(Authentication authentication) {
        try {
            return resolveCurrentCustomer(authentication)
                    .map(user -> notificationService.getTopCustomerNotifications(user.getId(), 5))
                    .orElse(Collections.emptyList());
        } catch (RuntimeException ex) {
            log.warn("Failed to load notification preview", ex);
            return Collections.emptyList();
        }
    }

    private Optional<User> resolveCurrentCustomer(Authentication authentication) {
        String email = extractEmail(authentication);
        if (email == null) return Optional.empty();
        return userRepository.findByEmail(email)
                .filter(user -> user.getRole() == Role.CUSTOMER);
    }
}
