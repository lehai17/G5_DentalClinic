package com.dentalclinic.service.notification;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.notification.NotificationReferenceType;
import com.dentalclinic.model.notification.NotificationType;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.schedule.BusySchedule;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.NotificationRepository;
import com.dentalclinic.repository.SupportTicketRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.util.DisplayTextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_DIGITS = Pattern.compile("\\D+");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final SupportTicketRepository supportTicketRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               AppointmentRepository appointmentRepository,
                               SupportTicketRepository supportTicketRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.supportTicketRepository = supportTicketRepository;
    }

    @Transactional
    public Notification createForCustomer(Long recipientId,
                                          NotificationType type,
                                          String title,
                                          String message,
                                          String url,
                                          NotificationReferenceType referenceType,
                                          Long referenceId) {
        // Notifications are side-effects and must never break core flows.
        if (recipientId == null) {
            return null;
        }
        try {
            User recipient = userRepository.findById(recipientId).orElse(null);
            if (recipient == null) {
                log.warn("Skip customer notification: recipient not found. recipientId={}, type={}, title={}",
                        recipientId, type, title);
                return null;
            }
            if (recipient.getRole() != Role.CUSTOMER) {
                log.warn("Skip customer notification: recipient role mismatch. recipientId={}, role={}, type={}, title={}",
                        recipientId, recipient.getRole(), type, title);
                return null;
            }

            Notification notification = new Notification();
            notification.setUser(recipient);
            notification.setType(type);
            notification.setTitle(sanitizeNotificationText(title));
            notification.setContent(sanitizeNotificationText(message));
            notification.setUrl(url);
            notification.setReferenceType(referenceType);
            notification.setReferenceId(referenceId);
            notification.setRead(false);
            notification.setCreatedAt(LocalDateTime.now(CLINIC_ZONE));
            notification.setReadAt(null);

            try {
                return notificationRepository.save(notification);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Skip customer notification due to DB constraint. recipientId={}, type={}, refType={}, refId={}, title={}",
                        recipientId, type, referenceType, referenceId, title, ex);
                return null;
            } catch (RuntimeException ex) {
                log.warn("Skip customer notification due to unexpected error. recipientId={}, type={}, refType={}, refId={}, title={}",
                        recipientId, type, referenceType, referenceId, title, ex);
                return null;
            }
        } catch (RuntimeException ex) {
            log.warn("Skip customer notification due to unexpected error. recipientId={}, type={}, refType={}, refId={}, title={}",
                    recipientId, type, referenceType, referenceId, title, ex);
            return null;
        }
    }

    @Transactional
    public Notification createForDentist(Long recipientId,
                                         NotificationType type,
                                         String title,
                                         String message,
                                         String url,
                                         NotificationReferenceType referenceType,
                                         Long referenceId) {
        // Dentist notifications are "nice-to-have": they must never break core flows like confirm/check-in.
        if (recipientId == null) {
            return null;
        }
        try {
            User recipient = userRepository.findById(recipientId).orElse(null);
            if (recipient == null) {
                log.warn("Skip dentist notification: recipient not found. recipientId={}, type={}, title={}",
                        recipientId, type, title);
                return null;
            }
            if (recipient.getRole() != Role.DENTIST) {
                log.warn("Skip dentist notification: recipient role mismatch. recipientId={}, role={}, type={}, title={}",
                        recipientId, recipient.getRole(), type, title);
                return null;
            }

            Notification notification = new Notification();
            notification.setUser(recipient);
            notification.setType(type);
            notification.setTitle(sanitizeNotificationText(title));
            notification.setContent(sanitizeNotificationText(message));
            notification.setUrl(url);
            notification.setReferenceType(referenceType);
            notification.setReferenceId(referenceId);
            notification.setRead(false);
            notification.setCreatedAt(LocalDateTime.now(CLINIC_ZONE));
            notification.setReadAt(null);

            try {
                return notificationRepository.save(notification);
            } catch (DataIntegrityViolationException ex) {
                log.warn("Skip dentist notification due to DB constraint. recipientId={}, type={}, refType={}, refId={}, title={}",
                        recipientId, type, referenceType, referenceId, title, ex);
                return null;
            } catch (RuntimeException ex) {
                log.warn("Skip dentist notification due to unexpected error. recipientId={}, type={}, refType={}, refId={}, title={}",
                        recipientId, type, referenceType, referenceId, title, ex);
                return null;
            }
        } catch (RuntimeException ex) {
            log.warn("Skip dentist notification due to unexpected error. recipientId={}, type={}, refType={}, refId={}, title={}",
                    recipientId, type, referenceType, referenceId, title, ex);
            return null;
        }
    }

    private String sanitizeNotificationText(String value) {
        return DisplayTextUtils.normalize(value);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getCustomerNotifications(Long recipientId, Pageable pageable) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getCustomerNotifications(Long recipientId, Pageable pageable, boolean unreadOnly) {
        if (unreadOnly) {
            return notificationRepository.findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(recipientId, pageable);
        }
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Notification> getTopCustomerNotifications(Long recipientId, int limit) {
        List<Notification> top = notificationRepository.findTop5ByUser_IdOrderByCreatedAtDesc(recipientId);
        if (limit >= top.size()) {
            return top;
        }
        return top.subList(0, limit);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long recipientId) {
        return notificationRepository.countByUser_IdAndIsReadFalse(recipientId);
    }

    @Transactional
    public void markRead(Long notificationId, Long recipientId) {
        int updated = notificationRepository.markAsRead(notificationId, recipientId);
        if (updated == 0) {
            throw new IllegalArgumentException("Kh\u00f4ng t\u00ecm th\u1ea5y th\u00f4ng b\u00e1o ho\u1eb7c b\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n truy c\u1eadp.");
        }
    }

    @Transactional
    public int markAllRead(Long recipientId) {
        return notificationRepository.markAllAsRead(recipientId);
    }

    @Transactional
    public void markUnread(Long notificationId, Long recipientId) {
        int updated = notificationRepository.markAsUnread(notificationId, recipientId);
        if (updated == 0) {
            throw new IllegalArgumentException("Kh\u00f4ng t\u00ecm th\u1ea5y th\u00f4ng b\u00e1o ho\u1eb7c b\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n truy c\u1eadp.");
        }
    }

    @Transactional
    public void deleteOwned(Long notificationId, Long recipientId) {
        int deleted = notificationRepository.deleteOwned(notificationId, recipientId);
        if (deleted == 0) {
            throw new IllegalArgumentException("Kh\u00f4ng t\u00ecm th\u1ea5y th\u00f4ng b\u00e1o ho\u1eb7c b\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n truy c\u1eadp.");
        }
    }

    /**
     * Dentist inbox: filter + search are done in-memory (expected small volume), then paginated.
     * Params:
     * - filter: all | unread | read
     * - category: ALL | APPOINTMENT | BUSY | SUPPORT
     */
    @Transactional
    public Page<Notification> getDentistNotifications(Long recipientId,
                                                      Pageable pageable,
                                                      String filter,
                                                      String category,
                                                      String keyword) {
        List<Notification> all = sanitizeDentistNotifications(
                notificationRepository.findByUser_IdOrderByCreatedAtDesc(recipientId),
                recipientId
        );

        String safeFilter = filter == null ? "all" : filter.trim().toLowerCase(Locale.ROOT);
        String safeCategory = category == null ? "ALL" : category.trim().toUpperCase(Locale.ROOT);
        String normalizedKeyword = normalizeSearch(keyword);

        List<Notification> filtered = new ArrayList<>();
        for (Notification n : all) {
            if (!matchesReadFilter(n, safeFilter)) {
                continue;
            }
            if (!matchesDentistCategory(n, safeCategory)) {
                continue;
            }
            if (!matchesKeyword(n, normalizedKeyword)) {
                continue;
            }
            filtered.add(n);
        }

        int pageNumber = Math.max(0, pageable.getPageNumber());
        int pageSize = Math.max(1, pageable.getPageSize());

        int fromIndex = Math.min(pageNumber * pageSize, filtered.size());
        int toIndex = Math.min(fromIndex + pageSize, filtered.size());

        return new org.springframework.data.domain.PageImpl<>(
                filtered.subList(fromIndex, toIndex),
                pageable,
                filtered.size()
        );
    }

    private List<Notification> sanitizeDentistNotifications(List<Notification> notifications, Long recipientId) {
        List<Notification> sanitized = new ArrayList<>();
        for (Notification notification : notifications) {
            if (notification == null) {
                continue;
            }

            boolean relatedAvailable = isRelatedTargetAvailable(notification);
            notification.setRelatedAvailable(relatedAvailable);

            if (!relatedAvailable && isSupportNotification(notification)) {
                notificationRepository.deleteOwned(notification.getId(), recipientId);
                continue;
            }

            sanitized.add(notification);
        }
        return sanitized;
    }

    private boolean isRelatedTargetAvailable(Notification notification) {
        if (notification == null || !isSupportNotification(notification)) {
            return true;
        }
        Long referenceId = notification.getReferenceId();
        if (referenceId == null) {
            return false;
        }
        return supportTicketRepository.existsById(referenceId);
    }

    private boolean isSupportNotification(Notification notification) {
        if (notification == null) {
            return false;
        }
        if (notification.getReferenceType() == NotificationReferenceType.TICKET) {
            return true;
        }
        String url = notification.getUrl();
        return url != null && url.startsWith("/dentist/support");
    }

    private boolean matchesReadFilter(Notification n, String filter) {
        if (filter == null || "all".equals(filter)) {
            return true;
        }
        if ("unread".equals(filter)) {
            return n != null && !n.isRead();
        }
        if ("read".equals(filter)) {
            return n != null && n.isRead();
        }
        return true;
    }

    private boolean matchesDentistCategory(Notification n, String category) {
        if (category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)) {
            return true;
        }
        String normalized = category.trim().toUpperCase(Locale.ROOT);
        String url = n == null ? null : n.getUrl();
        NotificationReferenceType refType = n == null ? null : n.getReferenceType();

        return switch (normalized) {
            case "APPOINTMENT", "LICH_HEN" -> refType == NotificationReferenceType.APPOINTMENT;
            case "BUSY", "LICH_BAN" -> url != null && url.startsWith("/dentist/busy-schedule");
            case "SUPPORT", "HO_TRO" -> refType == NotificationReferenceType.TICKET
                    || (url != null && url.startsWith("/dentist/support"));
            default -> true;
        };
    }

    private String normalizeSearch(String q) {
        if (q == null) {
            return "";
        }
        String trimmed = q.trim();
        if (trimmed.startsWith("#")) {
            trimmed = trimmed.substring(1).trim();
        }
        return trimmed.isBlank() ? "" : trimmed;
    }

    private boolean matchesKeyword(Notification n, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String lower = foldForSearch(keyword);
        String digits = NON_DIGITS.matcher(keyword).replaceAll("");

        boolean hasDigits = !digits.isEmpty();
        boolean allowPhoneLikeMatch = hasDigits && digits.length() >= 4;

        String title = n == null ? "" : safe(n.getTitle());
        String content = n == null ? "" : safe(n.getContent());
        String folded = foldForSearch(title + " " + content);

        if (folded.contains(lower)) {
            if (hasDigits && digits.length() < 4) {
                // Avoid matching everything via phone substring when keyword is short digits like "56".
                return matchesReferenceId(n, digits);
            }
            return true;
        }

        if (hasDigits && matchesReferenceId(n, digits)) {
            return true;
        }

        return hasDigits && containsDigitsInText(title, content, digits, allowPhoneLikeMatch);
    }

    private boolean matchesReferenceId(Notification n, String digits) {
        if (n == null || digits == null || digits.isBlank()) {
            return false;
        }
        Long ref = n.getReferenceId();
        if (ref == null) {
            return false;
        }
        return String.valueOf(ref).contains(digits);
    }

    private boolean containsDigitsInText(String title, String content, String digits, boolean allow) {
        if (!allow) {
            return false;
        }
        String combinedDigits = NON_DIGITS.matcher(safe(title) + " " + safe(content)).replaceAll("");
        return !combinedDigits.isEmpty() && combinedDigits.contains(digits);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String foldForSearch(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        String withoutMarks = DIACRITICS.matcher(normalized).replaceAll("");
        return withoutMarks.toLowerCase(Locale.ROOT).trim();
    }

    @Transactional(readOnly = true)
    public Notification getOwnedNotification(Long notificationId, Long recipientId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Kh\u00f4ng t\u00ecm th\u1ea5y th\u00f4ng b\u00e1o."));
        if (!notification.getUser().getId().equals(recipientId)) {
            throw new IllegalArgumentException("B\u1ea1n kh\u00f4ng c\u00f3 quy\u1ec1n truy c\u1eadp th\u00f4ng b\u00e1o n\u00e0y.");
        }
        notification.setRelatedAvailable(isRelatedTargetAvailable(notification));
        return notification;
    }

    // =========================
    // Dentist notifications (Inbox)
    // =========================

    @Transactional
    public void notifyDentistAppointmentConfirmed(Appointment appointment) {
        if (appointment == null || appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            return;
        }
        notifyDentistAppointmentEvent(
                appointment,
                NotificationType.BOOKING_CREATED,
                "L\u1ecbch h\u1eb9n #" + safeId(appointment) + " \u0111\u00e3 \u0111\u01b0\u1ee3c x\u00e1c nh\u1eadn",
                "L\u1ecbch h\u1eb9n #" + safeId(appointment) + " \u0111\u00e3 \u0111\u01b0\u1ee3c x\u00e1c nh\u1eadn. " + buildAppointmentContext(appointment),
                resolveWorkScheduleUrl(appointment)
        );
    }

    @Transactional
    public void notifyDentistAppointmentCheckedIn(Appointment appointment) {
        if (appointment == null || appointment.getStatus() != AppointmentStatus.CHECKED_IN) {
            return;
        }
        notifyDentistAppointmentEvent(
                appointment,
                NotificationType.BOOKING_UPDATED,
                "L\u1ecbch h\u1eb9n #" + safeId(appointment) + " \u0111\u00e3 check-in",
                "B\u1ec7nh nh\u00e2n \u0111\u00e3 check-in cho l\u1ecbch h\u1eb9n #" + safeId(appointment) + ". " + buildAppointmentContext(appointment),
                resolveWorkScheduleUrl(appointment)
        );
    }

    @Transactional
    public void notifyDentistAppointmentCancelled(Appointment appointment, String reason) {
        if (appointment == null || appointment.getStatus() != AppointmentStatus.CANCELLED) {
            return;
        }
        String suffix = (reason == null || reason.isBlank()) ? "" : (" L\u00fd do: " + reason.trim() + ".");
        notifyDentistAppointmentEvent(
                appointment,
                NotificationType.BOOKING_CANCELLED,
                "L\u1ecbch h\u1eb9n #" + safeId(appointment) + " \u0111\u00e3 b\u1ecb h\u1ee7y",
                "L\u1ecbch h\u1eb9n #" + safeId(appointment) + " \u0111\u00e3 b\u1ecb h\u1ee7y." + suffix + " " + buildAppointmentContext(appointment),
                resolveWorkScheduleUrl(appointment)
        );
    }

    @Transactional
    public void notifyDentistBusyScheduleApproved(BusySchedule request) {
        notifyDentistBusyScheduleEvent(
                request,
                NotificationType.BOOKING_UPDATED,
                "L\u1ecbch b\u1eadn \u0111\u00e3 \u0111\u01b0\u1ee3c duy\u1ec7t",
                buildBusyScheduleContext(request, "Y\u00eau c\u1ea7u l\u1ecbch b\u1eadn c\u1ee7a b\u1ea1n \u0111\u00e3 \u0111\u01b0\u1ee3c duy\u1ec7t."),
                "/dentist/busy-schedule"
        );
    }

    @Transactional
    public void notifyDentistBusyScheduleRejected(BusySchedule request) {
        notifyDentistBusyScheduleEvent(
                request,
                NotificationType.BOOKING_UPDATED,
                "L\u1ecbch b\u1eadn kh\u00f4ng \u0111\u01b0\u1ee3c duy\u1ec7t",
                buildBusyScheduleContext(request, "Y\u00eau c\u1ea7u l\u1ecbch b\u1eadn c\u1ee7a b\u1ea1n kh\u00f4ng \u0111\u01b0\u1ee3c duy\u1ec7t."),
                "/dentist/busy-schedule"
        );
    }

    @Transactional
    public void notifyDentistSupportTicketCreated(SupportTicket ticket) {
        if (ticket == null) return;
        Long dentistUserId = resolveTicketDentistUserId(ticket);
        if (dentistUserId == null) return;

        boolean existed = notificationRepository.existsByUser_IdAndTypeAndReferenceTypeAndReferenceId(
                dentistUserId,
                NotificationType.TICKET_STATUS_CHANGED,
                NotificationReferenceType.TICKET,
                ticket.getId()
        );
        if (existed) return;

        String customerName = ticket.getCustomerDisplayName() != null ? ticket.getCustomerDisplayName() : "B\u1ec7nh nh\u00e2n";
        String title = "Phi\u1ebfu h\u1ed7 tr\u1ee3 m\u1edbi #" + ticket.getId();
        String message = customerName + " \u0111\u00e3 t\u1ea1o phi\u1ebfu h\u1ed7 tr\u1ee3 m\u1edbi #" + ticket.getId()
                + (ticket.getTitle() == null || ticket.getTitle().isBlank() ? "." : (": " + ticket.getTitle().trim() + "."));

        createForDentist(
                dentistUserId,
                NotificationType.TICKET_STATUS_CHANGED,
                title,
                message,
                "/dentist/support/" + ticket.getId(),
                NotificationReferenceType.TICKET,
                ticket.getId()
        );
    }

    @Transactional
    public void notifyDentistSupportTicketCustomerMessage(SupportTicket ticket) {
        if (ticket == null) return;
        Long dentistUserId = resolveTicketDentistUserId(ticket);
        if (dentistUserId == null) return;

        String customerName = ticket.getCustomerDisplayName() != null ? ticket.getCustomerDisplayName() : "B\u1ec7nh nh\u00e2n";
        String title = "Tin nh\u1eafn m\u1edbi trong phi\u1ebfu #" + ticket.getId();
        String message = customerName + " v\u1eeba g\u1eedi tin nh\u1eafn m\u1edbi trong phi\u1ebfu h\u1ed7 tr\u1ee3 #" + ticket.getId() + ".";

        createForDentist(
                dentistUserId,
                NotificationType.TICKET_STATUS_CHANGED,
                title,
                message,
                "/dentist/support/" + ticket.getId(),
                NotificationReferenceType.TICKET,
                ticket.getId()
        );
    }

    private void notifyDentistAppointmentEvent(Appointment appointment,
                                              NotificationType type,
                                              String title,
                                              String message,
                                              String url) {
        if (appointment == null || appointment.getDentist() == null || appointment.getDentist().getUser() == null) {
            return;
        }
        Long dentistUserId = appointment.getDentist().getUser().getId();
        if (dentistUserId == null) return;

        boolean existed = notificationRepository.existsByUser_IdAndTypeAndReferenceTypeAndReferenceId(
                dentistUserId,
                type,
                NotificationReferenceType.APPOINTMENT,
                appointment.getId()
        );
        if (existed) return;

        createForDentist(
                dentistUserId,
                type,
                title,
                message,
                url,
                NotificationReferenceType.APPOINTMENT,
                appointment.getId()
        );
    }

    private void notifyDentistBusyScheduleEvent(BusySchedule request,
                                               NotificationType type,
                                               String title,
                                               String message,
                                               String url) {
        if (request == null || request.getDentist() == null || request.getDentist().getUser() == null) {
            return;
        }
        Long dentistUserId = request.getDentist().getUser().getId();
        if (dentistUserId == null) return;

        // We intentionally do not dedupe busy schedule notifications strictly,
        // because multiple requests can exist and we are not storing requestId as reference here.
        createForDentist(
                dentistUserId,
                type,
                title,
                message,
                url,
                null,
                null
        );
    }

    private Long resolveTicketDentistUserId(SupportTicket ticket) {
        if (ticket.getDentist() != null) {
            return ticket.getDentist().getId();
        }
        if (ticket.getAppointment() != null
                && ticket.getAppointment().getDentist() != null
                && ticket.getAppointment().getDentist().getUser() != null) {
            return ticket.getAppointment().getDentist().getUser().getId();
        }
        return null;
    }

    private String resolveWorkScheduleUrl(Appointment appointment) {
        if (appointment == null || appointment.getDate() == null) {
            return "/dentist/work-schedule";
        }
        LocalDate weekStart = appointment.getDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return "/dentist/work-schedule?weekStart=" + weekStart;
    }

    private String buildAppointmentContext(Appointment appointment) {
        if (appointment == null) {
            return "";
        }
        String patientName = (appointment.getCustomer() != null && appointment.getCustomer().getFullName() != null)
                ? appointment.getCustomer().getFullName()
                : "B\u1ec7nh nh\u00e2n";
        String phone = (appointment.getCustomer() != null && appointment.getCustomer().getPhone() != null)
                ? appointment.getCustomer().getPhone()
                : "";
        LocalDate date = appointment.getDate();
        LocalTime start = appointment.getStartTime();
        LocalTime end = appointment.getEndTime();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        DateTimeFormatter tf = DateTimeFormatter.ofPattern("HH:mm");

        StringBuilder sb = new StringBuilder();
        sb.append("B\u1ec7nh nh\u00e2n: ").append(patientName);
        if (!phone.isBlank()) {
            sb.append(" (").append(phone).append(")");
        }
        if (date != null) {
            sb.append(" \u2022 Ng\u00e0y: ").append(date.format(df));
        }
        if (start != null) {
            sb.append(" \u2022 Gi\u1edd: ").append(start.format(tf));
            if (end != null) {
                sb.append(" - ").append(end.format(tf));
            }
        }
        sb.append(".");
        return sb.toString();
    }

    private String buildBusyScheduleContext(BusySchedule request, String prefix) {
        if (request == null) return prefix == null ? "" : prefix.trim();
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        String range = "";
        if (request.getStartDate() != null && request.getEndDate() != null) {
            range = request.getStartDate().format(df) + " - " + request.getEndDate().format(df);
        } else if (request.getStartDate() != null) {
            range = request.getStartDate().format(df);
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();

        StringBuilder sb = new StringBuilder(prefix == null ? "" : prefix.trim());
        if (!range.isBlank()) {
            sb.append(" Th\u1eddi gian: ").append(range).append(".");
        }
        if (!reason.isBlank()) {
            sb.append(" L\u00fd do: ").append(reason).append(".");
        }
        return sb.toString().trim();
    }

    private String safeId(Appointment appointment) {
        return appointment != null && appointment.getId() != null ? String.valueOf(appointment.getId()) : "?";
    }

    @Transactional
    public void notifyBookingCreated(Appointment appointment) {
        Long recipientId = appointment.getCustomer().getUser().getId();
        boolean existed = notificationRepository.existsByUser_IdAndTypeAndReferenceTypeAndReferenceId(
                recipientId,
                NotificationType.BOOKING_CREATED,
                NotificationReferenceType.APPOINTMENT,
                appointment.getId()
        );
        if (existed) {
            return;
        }
        String title = "\u0110\u1eb7t l\u1ecbch th\u00e0nh c\u00f4ng";
        String message = "B\u1ea1n \u0111\u00e3 t\u1ea1o l\u1ecbch h\u1eb9n #" + appointment.getId() + " th\u00e0nh c\u00f4ng.";
        String url = "/customer/my-appointments#highlight=" + appointment.getId();
        createForCustomer(recipientId, NotificationType.BOOKING_CREATED, title, message, url,
                NotificationReferenceType.APPOINTMENT, appointment.getId());
    }

    @Transactional
    public void notifyBookingUpdated(Appointment appointment, String reason) {
        Long recipientId = appointment.getCustomer().getUser().getId();
        String title = "L\u1ecbch h\u1eb9n \u0111\u01b0\u1ee3c c\u1eadp nh\u1eadt";
        String message = "L\u1ecbch h\u1eb9n #" + appointment.getId() + " \u0111\u00e3 \u0111\u01b0\u1ee3c c\u1eadp nh\u1eadt"
                + (reason == null || reason.isBlank() ? "." : ": " + reason + ".");
        String url = "/customer/my-appointments#highlight=" + appointment.getId();
        createForCustomer(recipientId, NotificationType.BOOKING_UPDATED, title, message, url,
                NotificationReferenceType.APPOINTMENT, appointment.getId());
    }

    @Transactional
    public void notifyBookingCancelled(Appointment appointment, String reason) {
        Long recipientId = appointment.getCustomer().getUser().getId();
        String title = "L\u1ecbch h\u1eb9n \u0111\u00e3 b\u1ecb h\u1ee7y";
        String message = "L\u1ecbch h\u1eb9n #" + appointment.getId() + " \u0111\u00e3 b\u1ecb h\u1ee7y"
                + (reason == null || reason.isBlank() ? "." : ": " + reason + ".");
        String url = "/customer/my-appointments#highlight=" + appointment.getId();
        createForCustomer(recipientId, NotificationType.BOOKING_CANCELLED, title, message, url,
                NotificationReferenceType.APPOINTMENT, appointment.getId());
    }

    @Transactional
    public void notifyMedicalRecordCreated(Long customerUserId, Long recordId) {
        createForCustomer(
                customerUserId,
                NotificationType.MEDICAL_RECORD_CREATED,
                "C\u00f3 h\u1ed3 s\u01a1 kh\u00e1m m\u1edbi",
                "H\u1ed3 s\u01a1 kh\u00e1m m\u1edbi \u0111\u00e3 \u0111\u01b0\u1ee3c t\u1ea1o cho b\u1ea1n.",
                "/customer/medical-records",
                NotificationReferenceType.RECORD,
                recordId
        );
    }

    @Transactional
    public void notifyPrescriptionCreated(Long customerUserId, Long prescriptionId) {
        createForCustomer(
                customerUserId,
                NotificationType.PRESCRIPTION_CREATED,
                "C\u00f3 \u0111\u01a1n thu\u1ed1c m\u1edbi",
                "B\u00e1c s\u0129 \u0111\u00e3 t\u1ea1o \u0111\u01a1n thu\u1ed1c m\u1edbi cho b\u1ea1n.",
                "/customer/medical-records",
                NotificationReferenceType.PRESCRIPTION,
                prescriptionId
        );
    }

    @Transactional
    public void notifyFollowupRecommended(Long customerUserId, Long appointmentId) {
        createForCustomer(
                customerUserId,
                NotificationType.FOLLOWUP_RECOMMENDED,
                "C\u00f3 ch\u1ec9 \u0111\u1ecbnh t\u00e1i kh\u00e1m",
                "B\u1ea1n \u0111\u01b0\u1ee3c ch\u1ec9 \u0111\u1ecbnh t\u00e1i kh\u00e1m. Vui l\u00f2ng ki\u1ec3m tra l\u1ecbch h\u1eb9n.",
                "/customer/my-appointments#highlight=" + appointmentId,
                NotificationReferenceType.FOLLOWUP,
                appointmentId
        );
    }

    public void notifyWalletRefund(CustomerProfile customer, java.math.BigDecimal amount) {
        String amountStr = String.format("%,.0f", amount.doubleValue());
        createForCustomer(
                customer.getUser().getId(),
                NotificationType.BOOKING_CANCELLED,
                "Ho\u00e0n ti\u1ec1n \u0111\u1eb7t c\u1ecdc",
                "B\u1ea1n nh\u1eadn \u0111\u01b0\u1ee3c ho\u00e0n ti\u1ec1n " + amountStr + " VND t\u1eeb l\u1ecbch h\u1eb9n \u0111\u00e3 h\u1ee7y. S\u1ed1 d\u01b0 v\u00ed hi\u1ec7n t\u1ea1i c\u00f3 th\u1ec3 d\u00f9ng cho l\u1ea7n \u0111\u1eb7t ti\u1ebfp theo.",
                "/customer/wallet",
                null,
                null
        );
    }

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void sendAppointmentReminderNotifications() {
        LocalDateTime now = LocalDateTime.now(CLINIC_ZONE);
        LocalDate targetDate = now.plusHours(24).toLocalDate();

        List<Appointment> appointments = appointmentRepository.findByDateAndStatusIn(
                targetDate,
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)
        );

        for (Appointment appointment : appointments) {
            if (appointment.getCustomer() == null || appointment.getCustomer().getUser() == null) {
                continue;
            }
            LocalDateTime start = LocalDateTime.of(appointment.getDate(), appointment.getStartTime());
            long minutesDiff = java.time.Duration.between(now, start).toMinutes();
            if (minutesDiff < 23 * 60 || minutesDiff > 25 * 60) {
                continue;
            }

            Long recipientId = appointment.getCustomer().getUser().getId();
            boolean sent = notificationRepository.existsByUser_IdAndTypeAndReferenceTypeAndReferenceId(
                    recipientId,
                    NotificationType.APPOINTMENT_REMINDER,
                    NotificationReferenceType.APPOINTMENT,
                    appointment.getId()
            );
            if (sent) {
                continue;
            }

            String title = "Nh\u1eafc l\u1ecbch h\u1eb9n";
            String message = "B\u1ea1n c\u00f3 l\u1ecbch h\u1eb9n #" + appointment.getId() + " v\u00e0o ng\u00e0y "
                    + appointment.getDate() + " l\u00fac " + appointment.getStartTime() + ".";
            String url = "/customer/my-appointments#highlight=" + appointment.getId();
            createForCustomer(recipientId, NotificationType.APPOINTMENT_REMINDER, title, message, url,
                    NotificationReferenceType.APPOINTMENT, appointment.getId());
        }
    }
}

