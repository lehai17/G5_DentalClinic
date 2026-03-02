package com.dentalclinic.controller.customer;

import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.notification.NotificationReferenceType;
import com.dentalclinic.model.notification.NotificationType;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.SupportTicketRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/notifications")
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerNotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final SupportTicketRepository supportTicketRepository;

    public CustomerNotificationController(NotificationService notificationService,
                                          UserRepository userRepository,
                                          SupportTicketRepository supportTicketRepository) {
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.supportTicketRepository = supportTicketRepository;
    }

    @GetMapping
    public String list(@AuthenticationPrincipal UserDetails principal,
                       @RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "all") String filter,
                       Model model) {
        Long customerId = getCurrentCustomerId(principal);
        var pageable = PageRequest.of(Math.max(0, page), 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        boolean unreadOnly = "unread".equalsIgnoreCase(filter);
        var notificationsPage = notificationService.getCustomerNotifications(customerId, pageable, unreadOnly);

        model.addAttribute("notifications", notificationsPage.getContent());
        model.addAttribute("currentPage", notificationsPage.getNumber());
        model.addAttribute("totalPages", notificationsPage.getTotalPages());
        model.addAttribute("selectedFilter", filter);
        model.addAttribute("active", "notifications");
        return "customer/notifications";
    }

    @PostMapping("/{id}/read")
    public String markRead(@PathVariable Long id,
                           @AuthenticationPrincipal UserDetails principal,
                           @RequestParam(required = false) String returnTo,
                           RedirectAttributes redirectAttributes) {
        Long customerId = getCurrentCustomerId(principal);
        try {
            notificationService.markRead(id, customerId);
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:" + normalizeReturnUrl(returnTo);
    }

    @PostMapping("/read-all")
    public String markAllRead(@AuthenticationPrincipal UserDetails principal,
                              @RequestParam(required = false) String returnTo) {
        Long customerId = getCurrentCustomerId(principal);
        notificationService.markAllRead(customerId);
        return "redirect:" + normalizeReturnUrl(returnTo);
    }

    @GetMapping("/{id}/open")
    public String openNotification(@PathVariable Long id,
                                   @AuthenticationPrincipal UserDetails principal,
                                   RedirectAttributes redirectAttributes) {
        Long customerId = getCurrentCustomerId(principal);
        try {
            Notification notification = notificationService.getOwnedNotification(id, customerId);
            notificationService.markRead(id, customerId);
            String targetUrl = resolveNotificationTargetUrl(notification, customerId);
            return "redirect:" + targetUrl;
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/customer/notifications";
        }
    }

    private String resolveNotificationTargetUrl(Notification notification, Long customerId) {
        String url = notification.getUrl();
        if (isSafeInternalUrl(url)) {
            return url;
        }

        NotificationReferenceType referenceType = notification.getReferenceType();
        Long referenceId = notification.getReferenceId();

        if (referenceType == null || referenceId == null) {
            Long supportTicketId = extractSupportTicketId(notification);
            if (supportTicketId != null) {
                return "/support/" + supportTicketId;
            }
            if (isSupportNotificationType(notification.getType())) {
                Long latestAnsweredTicketId = findLatestAnsweredTicketId(customerId);
                if (latestAnsweredTicketId != null) {
                    return "/support/" + latestAnsweredTicketId;
                }
            }
            return "/customer/notifications";
        }

        return switch (referenceType) {
            case APPOINTMENT, FOLLOWUP -> "/customer/my-appointments#highlight=" + referenceId;
            case TICKET -> "/support/" + referenceId;
            case RECORD -> "/patient/medical-records";
            case PRESCRIPTION -> "/patient/prescriptions";
        };
    }

    private boolean isSafeInternalUrl(String url) {
        if (url == null || url.isBlank()) return false;
        if (!url.startsWith("/")) return false;
        return !(url.startsWith("//") || url.contains("://"));
    }

    private Long extractSupportTicketId(Notification notification) {
        if (notification.getReferenceId() != null && isSupportNotificationType(notification.getType())) {
            return notification.getReferenceId();
        }

        String content = notification.getContent();
        if (content == null || !isSupportNotificationType(notification.getType())) {
            return null;
        }

        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("#(\\d+)").matcher(content);
        if (matcher.find()) {
            try {
                return Long.parseLong(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isSupportNotificationType(NotificationType type) {
        return type == NotificationType.TICKET_ANSWERED
                || type == NotificationType.TICKET_STATUS_CHANGED
                || type == NotificationType.TICKET_NEW_REPLY
                || type == NotificationType.SUPPORT;
    }

    private Long findLatestAnsweredTicketId(Long customerId) {
        return supportTicketRepository.findFirstByCustomer_IdAndAnswerIsNotNullOrderByCreatedAtDesc(customerId)
                .map(SupportTicket::getId)
                .orElse(null);
    }

    private String normalizeReturnUrl(String returnTo) {
        if (returnTo == null || returnTo.isBlank()) {
            return "/customer/notifications";
        }
        if (!returnTo.startsWith("/")) {
            return "/customer/notifications";
        }
        return returnTo;
    }

    private Long getCurrentCustomerId(UserDetails principal) {
        if (principal == null || principal.getUsername() == null || principal.getUsername().isBlank()) {
            throw new IllegalArgumentException("Không xác định được tài khoản hiện tại.");
        }
        User user = userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tài khoản hiện tại."));
        if (user.getRole() != Role.CUSTOMER) {
            throw new IllegalArgumentException("Bạn không có quyền truy cập thông báo khách hàng.");
        }
        return user.getId();
    }
}
