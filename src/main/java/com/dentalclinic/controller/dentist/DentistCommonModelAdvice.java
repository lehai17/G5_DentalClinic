package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.user.Role;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

/**
 * Inject small shared attributes for Dentist pages without touching each controller.
 */
@ControllerAdvice(basePackages = "com.dentalclinic.controller.dentist")
public class DentistCommonModelAdvice {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public DentistCommonModelAdvice(NotificationService notificationService,
                                    UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @ModelAttribute
    public void addDentistCommonModel(@AuthenticationPrincipal UserDetails principal, Model model) {
        if (model == null || principal == null || principal.getUsername() == null || principal.getUsername().isBlank()) {
            return;
        }

        try {
            userRepository.findByEmail(principal.getUsername()).ifPresent(user -> {
                if (user.getRole() == Role.DENTIST) {
                    model.addAttribute("unreadCount", notificationService.countUnread(user.getId()));
                }
            });
        } catch (RuntimeException ignored) {
            // Never block Dentist pages if notifications fail for any reason.
        }
    }
}
