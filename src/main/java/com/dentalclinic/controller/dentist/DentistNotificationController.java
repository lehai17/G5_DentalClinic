package com.dentalclinic.controller.dentist;

import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.notification.NotificationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/dentist/notifications")
@PreAuthorize("hasRole('DENTIST')")
public class DentistNotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public DentistNotificationController(NotificationService notificationService,
                                         UserRepository userRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "all") String filter,
                       @RequestParam(defaultValue = "ALL") String category,
                       @RequestParam(required = false) String q,
                       HttpServletRequest request,
                       Model model) {
        Long dentistUserId = getCurrentDentistId(principal);

        var pageable = PageRequest.of(
                Math.max(0, page),
                10,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        var notificationsPage = notificationService.getDentistNotifications(dentistUserId, pageable, filter, category, q);

        String returnTo = buildReturnTo(request);

        model.addAttribute("notifications", notificationsPage.getContent());
        model.addAttribute("currentPage", notificationsPage.getNumber());
        model.addAttribute("totalPages", notificationsPage.getTotalPages());
        model.addAttribute("selectedFilter", filter == null ? "all" : filter);
        model.addAttribute("selectedCategory", category == null ? "ALL" : category);
        model.addAttribute("keyword", q == null ? "" : q);
        model.addAttribute("unreadCount", notificationService.countUnread(dentistUserId));
        model.addAttribute("returnTo", returnTo);
        model.addAttribute("activePage", "notifications");

        return "Dentist/notifications";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails principal,
                           @RequestParam(required = false) String returnTo,
                           RedirectAttributes redirectAttributes) {
        Long dentistUserId = getCurrentDentistId(principal);
        try {
            notificationService.markRead(id, dentistUserId);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + normalizeReturnTo(returnTo);
    }

    @PostMapping("/{id}/unread")
    public String markUnread(@PathVariable Long id,
                             @AuthenticationPrincipal UserDetails principal,
                             @RequestParam(required = false) String returnTo,
                             RedirectAttributes redirectAttributes) {
        Long dentistUserId = getCurrentDentistId(principal);
        try {
            notificationService.markUnread(id, dentistUserId);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + normalizeReturnTo(returnTo);
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @AuthenticationPrincipal UserDetails principal,
                         @RequestParam(required = false) String returnTo,
                         RedirectAttributes redirectAttributes) {
        Long dentistUserId = getCurrentDentistId(principal);
        try {
            notificationService.deleteOwned(id, dentistUserId);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + normalizeReturnTo(returnTo);
    }

    @PostMapping("/read-all")
    public String markAllRead(@AuthenticationPrincipal UserDetails principal,
                              @RequestParam(required = false) String returnTo,
                              RedirectAttributes redirectAttributes) {
        Long dentistUserId = getCurrentDentistId(principal);
        try {
            notificationService.markAllRead(dentistUserId);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + normalizeReturnTo(returnTo);
    }

    private Long getCurrentDentistId(UserDetails principal) {
        if (principal == null || principal.getUsername() == null || principal.getUsername().isBlank()) {
            throw new IllegalArgumentException("Khong xac dinh duoc tai khoan hien tai.");
        }
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Khong tim thay tai khoan hien tai."));
        if (user.getRole() != Role.DENTIST) {
            throw new IllegalArgumentException("Ban khong co quyen truy cap thong bao bac si.");
        }
        return user.getId();
    }

    private String buildReturnTo(HttpServletRequest request) {
        if (request == null) {
            return "/dentist/notifications";
        }
        String qs = request.getQueryString();
        if (qs == null || qs.isBlank()) {
            return "/dentist/notifications";
        }
        return "/dentist/notifications?" + qs;
    }

    private String normalizeReturnTo(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return "/dentist/notifications";
        }
        if (!returnTo.startsWith("/")) {
            return "/dentist/notifications";
        }
        return returnTo;
    }
}
