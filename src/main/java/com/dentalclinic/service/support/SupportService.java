package com.dentalclinic.service.support;

import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.exception.SupportAccessDeniedException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.notification.NotificationReferenceType;
import com.dentalclinic.model.notification.NotificationType;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.profile.StaffProfile;
import com.dentalclinic.model.support.SupportStatus;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.StaffProfileRepository;
import com.dentalclinic.repository.SupportTicketRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class SupportService {

    private static final String TRANSCRIPT_PREFIX = "CHATV2";
    private static final String SENDER_CUSTOMER = "CUSTOMER";
    private static final String SENDER_STAFF = "STAFF";
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_MESSAGE_LENGTH = 2000;
    private static final int APPOINTMENT_SUPPORT_WINDOW_DAYS = 14;

    private final SupportTicketRepository supportTicketRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final DentistProfileRepository dentistProfileRepository;

    public SupportService(SupportTicketRepository supportTicketRepository,
                          NotificationService notificationService,
                          UserRepository userRepository,
                          AppointmentRepository appointmentRepository,
                          CustomerProfileRepository customerProfileRepository,
                          StaffProfileRepository staffProfileRepository,
                          DentistProfileRepository dentistProfileRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.dentistProfileRepository = dentistProfileRepository;
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(UserDetails principal) {
        if (principal == null || principal.getUsername() == null || principal.getUsername().isBlank()) {
            throw new SupportAccessDeniedException("Không xác định được tài khoản hiện tại.");
        }
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new SupportAccessDeniedException("Không tìm thấy người dùng hiện tại."));
    }

    @Transactional
    public SupportTicket createTicket(Long customerUserId, Long appointmentId, String title, String question) {
        User customer = requireCustomer(customerUserId);
        String safeTitle = normalizeTitle(title);
        String safeQuestion = normalizeMessage(question, "Nội dung câu hỏi không được để trống.");

        Appointment appointment = null;
        User assignedResponder = null;
        User assignedDentist = null;

        if (appointmentId != null) {
            appointment = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, customerUserId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy ca khám phù hợp để hỗ trợ."));

            validateAppointmentSupportEligibility(appointment);

            if (appointment.getDentist() != null && appointment.getDentist().getUser() != null) {
                assignedResponder = appointment.getDentist().getUser();
                assignedDentist = appointment.getDentist().getUser();
            }
        }

        LocalDateTime now = LocalDateTime.now();
        List<TranscriptEntry> transcriptEntries = new ArrayList<>();
        transcriptEntries.add(new TranscriptEntry(SENDER_CUSTOMER, resolveCustomerLabel(customer), safeQuestion, now));

        SupportTicket ticket = new SupportTicket();
        ticket.setCustomer(customer);
        ticket.setAppointment(appointment);
        ticket.setStaff(assignedResponder);
        ticket.setDentist(assignedDentist);
        ticket.setTitle(safeTitle);
        ticket.setQuestion(serializeTranscript(transcriptEntries, false));
        ticket.setAnswer(null);
        ticket.setStatus(SupportStatus.OPEN);
        ticket.setCreatedAt(now);

        SupportTicket saved = supportTicketRepository.save(ticket);
        hydrateTicket(saved);

        // Dentist inbox notification: new support ticket created by customer
        notificationService.notifyDentistSupportTicketCreated(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsForSupport(Long customerUserId) {
        requireCustomer(customerUserId);
        return appointmentRepository.findByCustomer_User_IdOrderByDateDesc(customerUserId)
                .stream()
                .filter(this::isEligibleSupportAppointment)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SupportTicket> getMyTicketsPage(Long customerUserId, int page, int size, String keyword, String sort) {
        requireCustomer(customerUserId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        List<SupportTicket> filteredTickets = supportTicketRepository.findByCustomer_IdOrderByCreatedAtDesc(customerUserId)
                .stream()
                .map(this::hydrateTicket)
                .sorted(resolveTicketComparator(sort))
                .filter(ticket -> matchesTicketKeyword(ticket, keyword))
                .toList();

        int fromIndex = Math.min(safePage * safeSize, filteredTickets.size());
        int toIndex = Math.min(fromIndex + safeSize, filteredTickets.size());

        return new org.springframework.data.domain.PageImpl<>(
                filteredTickets.subList(fromIndex, toIndex),
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt")),
                filteredTickets.size()
        );
    }

    private Comparator<SupportTicket> resolveTicketComparator(String sort) {
        String normalized = sort == null ? "newest" : sort.trim().toLowerCase(Locale.ROOT);

        Comparator<SupportTicket> byNewest = Comparator
                .comparing(SupportTicket::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo))
                .reversed();

        Comparator<SupportTicket> byOldest = Comparator
                .comparing(SupportTicket::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo));

        Comparator<SupportTicket> byStatus = Comparator
                .comparing((SupportTicket ticket) -> supportStatusRank(ticket.getDisplayStatus()))
                .thenComparing(SupportTicket::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo).reversed());

        return switch (normalized) {
            case "oldest" -> byOldest;
            case "status" -> byStatus;
            default -> byNewest;
        };
    }

    private int supportStatusRank(String status) {
        if (status == null) {
            return 99;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "OPEN" -> 0;
            case "ANSWERED" -> 1;
            case "CLOSED" -> 2;
            default -> 99;
        };
    }

    @Transactional(readOnly = true)
    public SupportTicket getTicketDetail(Long userId, Long ticketId) {
        User user = requireUser(userId);
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (user.getRole() == Role.CUSTOMER && !ticket.getCustomer().getId().equals(userId)) {
            throw new SupportAccessDeniedException("Bạn không có quyền xem yêu cầu hỗ trợ này.");
        }
        return hydrateTicket(ticket);
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> getAllTickets(String status, Long staffUserId) {
        User staff = requireUser(staffUserId);
        if (staff.getRole() != Role.STAFF && staff.getRole() != Role.ADMIN) {
            throw new SupportAccessDeniedException("Bạn không có quyền xem danh sách hỗ trợ.");
        }

        String normalizedStatus = normalizeStatusFilter(status);
        List<SupportTicket> tickets = (normalizedStatus == null)
                ? supportTicketRepository.findAllByOrderByCreatedAtDesc()
                : supportTicketRepository.findByStatusOrderByCreatedAtDesc(parseStatus(normalizedStatus));

        return tickets.stream()
                .map(this::hydrateTicket)
                .filter(ticket -> matchesDisplayStatus(ticket, normalizedStatus))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> getDentistVisibleTickets(Long dentistUserId, String status) {
        User dentist = requireUser(dentistUserId);
        if (dentist.getRole() != Role.DENTIST) {
            throw new SupportAccessDeniedException("Chỉ bác sĩ mới có quyền xem danh sách này.");
        }

        String normalizedStatus = normalizeStatusFilter(status);
        List<SupportTicket> tickets = (normalizedStatus == null)
                ? supportTicketRepository.findVisibleToDentist(dentistUserId)
                : supportTicketRepository.findVisibleToDentistByStatus(dentistUserId, parseStatus(normalizedStatus));

        return tickets.stream()
                .map(this::hydrateTicket)
                .filter(ticket -> matchesDisplayStatus(ticket, normalizedStatus))
                .toList();
    }

    @Transactional(readOnly = true)
    public SupportTicket getDentistTicketDetail(Long dentistUserId, Long ticketId) {
        User dentist = requireUser(dentistUserId);
        if (dentist.getRole() != Role.DENTIST) {
            throw new SupportAccessDeniedException("Chỉ bác sĩ mới có quyền xem chi tiết phiếu hỗ trợ.");
        }

        SupportTicket ticket = supportTicketRepository.findVisibleToDentistById(ticketId, dentistUserId)
                .orElseThrow(() -> new SupportAccessDeniedException("Bạn không có quyền xem phiếu hỗ trợ này."));
        return hydrateTicket(ticket);
    }

    @Transactional
    public SupportTicket replyTicket(Long customerUserId, Long ticketId, String message) {
        User customer = requireCustomer(customerUserId);
        String safeMessage = normalizeMessage(message, "Nội dung phản hồi không được để trống.");
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (!ticket.getCustomer().getId().equals(customerUserId)) {
            throw new SupportAccessDeniedException("Bạn không có quyền phản hồi phiếu hỗ trợ này.");
        }
        if (isConversationClosed(ticket)) {
            throw new BusinessException("Phiếu hỗ trợ đã đóng, không thể phản hồi thêm.");
        }

        List<TranscriptEntry> transcriptEntries = parseTranscript(ticket);
        transcriptEntries.add(new TranscriptEntry(SENDER_CUSTOMER, resolveCustomerLabel(customer), safeMessage, LocalDateTime.now()));

        ticket.setQuestion(serializeTranscript(transcriptEntries, false));
        ticket.setStatus(SupportStatus.OPEN);

        SupportTicket saved = supportTicketRepository.save(ticket);
        SupportTicket hydrated = hydrateTicket(saved);

        // Dentist inbox notification: customer sent a new message in support ticket
        notificationService.notifyDentistSupportTicketCustomerMessage(hydrated);
        return hydrated;
    }

    @Transactional
    public SupportTicket answerTicket(Long responderUserId, Long ticketId, String answer) {
        User responder = requireSupportResponder(responderUserId);
        String safeAnswer = normalizeMessage(answer, "Nội dung trả lời không được để trống.");

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (isConversationClosed(ticket)) {
            throw new BusinessException("Phiếu hỗ trợ đã đóng, không thể phản hồi thêm.");
        }

        if (responder.getRole() == Role.DENTIST) {
            validateDentistCanAnswer(responder.getId(), ticket);
            ticket.setDentist(responder);
        }

        List<TranscriptEntry> transcriptEntries = parseTranscript(ticket);
        transcriptEntries.add(new TranscriptEntry(SENDER_STAFF, resolveResponderLabel(responder), safeAnswer, LocalDateTime.now()));

        ticket.setStaff(responder);
        ticket.setQuestion(serializeTranscript(transcriptEntries, false));
        ticket.setAnswer(safeAnswer);
        ticket.setStatus(SupportStatus.ANSWERED);

        SupportTicket saved = supportTicketRepository.save(ticket);
        hydrateTicket(saved);

        notificationService.createForCustomer(
                saved.getCustomer().getId(),
                NotificationType.TICKET_ANSWERED,
                "Phiếu hỗ trợ đã được phản hồi",
                "Phiếu hỗ trợ #" + saved.getId() + " đã có phản hồi mới.",
                "/support/" + saved.getId(),
                NotificationReferenceType.TICKET,
                saved.getId()
        );

        return saved;
    }

    @Transactional
    public SupportTicket closeTicket(Long staffUserId, Long ticketId) {
        User staff = requireStaffOrAdmin(staffUserId);
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (isConversationClosed(ticket)) {
            throw new BusinessException("Phiếu hỗ trợ đã được đóng trước đó.");
        }

        List<TranscriptEntry> transcriptEntries = parseTranscript(ticket);
        ticket.setQuestion(serializeTranscript(transcriptEntries, true));
        if (ticket.getStaff() == null) {
            ticket.setStaff(staff);
        }

        SupportTicket saved = supportTicketRepository.save(ticket);
        hydrateTicket(saved);

        notificationService.createForCustomer(
                saved.getCustomer().getId(),
                NotificationType.TICKET_STATUS_CHANGED,
                "Phiếu hỗ trợ đã được đóng",
                "Phiếu hỗ trợ #" + saved.getId() + " đã được đóng.",
                "/support/" + saved.getId(),
                NotificationReferenceType.TICKET,
                saved.getId()
        );

        return saved;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng."));
    }

    private User requireCustomer(Long userId) {
        User user = requireUser(userId);
        if (user.getRole() != Role.CUSTOMER) {
            throw new SupportAccessDeniedException("Chỉ khách hàng mới được thực hiện thao tác này.");
        }
        return user;
    }

    private User requireStaffOrAdmin(Long userId) {
        User user = requireUser(userId);
        if (user.getRole() != Role.STAFF && user.getRole() != Role.ADMIN) {
            throw new SupportAccessDeniedException("Bạn không có quyền thực hiện thao tác này.");
        }
        return user;
    }

    private User requireSupportResponder(Long userId) {
        User user = requireUser(userId);
        if (user.getRole() != Role.STAFF && user.getRole() != Role.DENTIST && user.getRole() != Role.ADMIN) {
            throw new SupportAccessDeniedException("Chỉ nhân viên, bác sĩ hoặc admin mới được phản hồi phiếu hỗ trợ.");
        }
        return user;
    }

    private void validateDentistCanAnswer(Long dentistUserId, SupportTicket ticket) {
        if (ticket.getAppointment() == null) {
            return;
        }

        if (ticket.getAppointment().getDentist() == null || ticket.getAppointment().getDentist().getUser() == null) {
            throw new SupportAccessDeniedException("Phiếu này chưa gắn bác sĩ ca khám, bác sĩ không thể phản hồi.");
        }

        Long assignedDentistUserId = ticket.getAppointment().getDentist().getUser().getId();
        if (!assignedDentistUserId.equals(dentistUserId)) {
            throw new SupportAccessDeniedException("Bạn không có quyền phản hồi phiếu liên quan ca khám này.");
        }
    }

    private SupportStatus parseStatus(String status) {
        String safe = status == null ? "" : status.trim().toUpperCase();
        if ("CLOSED".equals(safe)) {
            return SupportStatus.ANSWERED;
        }
        try {
            return SupportStatus.valueOf(safe);
        } catch (Exception e) {
            throw new BusinessException("Trạng thái lọc không hợp lệ.");
        }
    }

    private void validateAppointmentSupportEligibility(Appointment appointment) {
        if (!isEligibleSupportAppointment(appointment)) {
            throw new BusinessException("Chỉ có thể gửi hỗ trợ cho ca khám đã hoàn thành, có bác sĩ phụ trách và còn trong thời hạn 14 ngày.");
        }
    }

    private boolean isEligibleSupportAppointment(Appointment appointment) {
        if (appointment == null) {
            return false;
        }
        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            return false;
        }
        if (appointment.getDentist() == null || appointment.getDentist().getUser() == null) {
            return false;
        }
        if (appointment.getDate() == null) {
            return false;
        }
        return !appointment.getDate().plusDays(APPOINTMENT_SUPPORT_WINDOW_DAYS).isBefore(LocalDateTime.now().toLocalDate());
    }

    private String normalizeStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        String safe = status.trim().toUpperCase();
        if (!"OPEN".equals(safe) && !"ANSWERED".equals(safe) && !"CLOSED".equals(safe)) {
            throw new BusinessException("Trạng thái lọc không hợp lệ.");
        }
        return safe;
    }

    private boolean matchesDisplayStatus(SupportTicket ticket, String normalizedStatus) {
        if (normalizedStatus == null) {
            return true;
        }
        return normalizedStatus.equalsIgnoreCase(ticket.getDisplayStatus());
    }

    private String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException("Tiêu đề không được để trống.");
        }
        String safeTitle = title.trim();
        if (safeTitle.length() > MAX_TITLE_LENGTH) {
            throw new BusinessException("Tiêu đề quá dài.");
        }
        return safeTitle;
    }

    private String normalizeMessage(String message, String emptyMessage) {
        if (message == null || message.trim().isEmpty()) {
            throw new BusinessException(emptyMessage);
        }
        String safeMessage = message.trim();
        if (safeMessage.length() > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("Nội dung vượt quá giới hạn cho phép.");
        }
        return safeMessage;
    }

    private SupportTicket hydrateTicket(SupportTicket ticket) {
        List<TranscriptEntry> transcriptEntries = parseTranscript(ticket);
        List<SupportTicket.ConversationEntry> conversationEntries = transcriptEntries.stream()
                .map(entry -> new SupportTicket.ConversationEntry(
                        entry.senderType,
                        entry.senderLabel,
                        entry.content,
                        entry.createdAt,
                        SENDER_CUSTOMER.equals(entry.senderType)
                ))
                .toList();

        ticket.setConversationEntries(conversationEntries);
        ticket.setCustomerDisplayName(resolveCustomerLabel(ticket.getCustomer()));
        ticket.setResponderDisplayName(resolveTicketResponderDisplayName(ticket));
        ticket.setLatestCustomerMessage(transcriptEntries.stream()
                .filter(entry -> SENDER_CUSTOMER.equals(entry.senderType))
                .map(entry -> entry.content)
                .reduce((first, second) -> second)
                .orElse(""));
        ticket.setLatestStaffReply(transcriptEntries.stream()
                .filter(entry -> SENDER_STAFF.equals(entry.senderType))
                .map(entry -> entry.content)
                .reduce((first, second) -> second)
                .orElse(ticket.getAnswer()));
        ticket.setServiceLabel(buildServiceLabel(ticket.getAppointment()));
        ticket.setDisplayStatus(isConversationClosed(ticket) ? "CLOSED" : ticket.getStatus().name());
        return ticket;
    }

    private boolean isConversationClosed(SupportTicket ticket) {
        String rawQuestion = ticket.getQuestion();
        if (rawQuestion == null || rawQuestion.isBlank()) {
            return false;
        }
        String[] lines = rawQuestion.split("\\R", -1);
        return lines.length > 0
                && lines[0].startsWith(TRANSCRIPT_PREFIX + "|")
                && lines[0].endsWith("|1");
    }

    private List<TranscriptEntry> parseTranscript(SupportTicket ticket) {
        String rawQuestion = ticket.getQuestion();
        List<TranscriptEntry> entries = new ArrayList<>();

        if (rawQuestion != null && rawQuestion.startsWith(TRANSCRIPT_PREFIX + "|")) {
            String[] lines = rawQuestion.split("\\R");
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i];
                if (line == null || line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\\|", 4);
                if (parts.length < 4) {
                    continue;
                }
                entries.add(new TranscriptEntry(
                        parts[0],
                        decode(parts[2]),
                        decode(parts[3]),
                        LocalDateTime.parse(parts[1])
                ));
            }
            return entries;
        }

        if (rawQuestion != null && !rawQuestion.isBlank()) {
            entries.add(new TranscriptEntry(
                    SENDER_CUSTOMER,
                    ticket.getCustomer() != null ? resolveCustomerLabel(ticket.getCustomer()) : "Khách hàng",
                    rawQuestion,
                    ticket.getCreatedAt()
            ));
        }

        if (ticket.getAnswer() != null && !ticket.getAnswer().isBlank()) {
            entries.add(new TranscriptEntry(
                    SENDER_STAFF,
                    ticket.getStaff() != null ? resolveResponderLabel(ticket.getStaff()) : "Phòng khám",
                    ticket.getAnswer(),
                    ticket.getCreatedAt()
            ));
        }

        return entries;
    }

    private String serializeTranscript(List<TranscriptEntry> entries, boolean closed) {
        StringBuilder builder = new StringBuilder();
        builder.append(TRANSCRIPT_PREFIX).append("|META|").append(closed ? "1" : "0");

        for (TranscriptEntry entry : entries) {
            builder.append("\n")
                    .append(entry.senderType).append("|")
                    .append(entry.createdAt).append("|")
                    .append(encode(entry.senderLabel)).append("|")
                    .append(encode(entry.content));
        }

        return builder.toString();
    }

    private String encode(String value) {
        return Base64.getEncoder().encodeToString((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
    }

    private String decode(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return "";
        }
    }

    private String resolveCustomerLabel(User customer) {
        if (customer == null) {
            return "Khách hàng";
        }
        return customerProfileRepository.findByUser_Id(customer.getId())
                .map(CustomerProfile::getFullName)
                .filter(name -> name != null && !name.isBlank())
                .orElseGet(() -> customer.getEmail() != null ? customer.getEmail() : "Khách hàng");
    }

    private String resolveResponderLabel(User responder) {
        if (responder == null) {
            return "Phòng khám";
        }
        String fullName = stripRolePrefix(resolveUserFullName(responder));
        if (responder.getRole() == Role.DENTIST) {
            return "Bác sĩ " + fullName;
        }
        if (responder.getRole() == Role.ADMIN) {
            return "Quản trị viên " + fullName;
        }
        return "Lễ tân " + fullName;
    }

    private String stripRolePrefix(String fullName) {
        if (fullName == null) {
            return "";
        }

        String normalized = fullName.trim();
        if (normalized.isEmpty()) {
            return normalized;
        }

        String lower = normalized.toLowerCase(Locale.ROOT);
        if (lower.startsWith("bác sĩ ")) {
            return normalized.substring(7).trim();
        }
        if (lower.startsWith("bac si ")) {
            return normalized.substring(7).trim();
        }
        if (lower.startsWith("lễ tân ")) {
            return normalized.substring(7).trim();
        }
        if (lower.startsWith("le tan ")) {
            return normalized.substring(7).trim();
        }
        if (lower.startsWith("quản trị viên ")) {
            return normalized.substring(13).trim();
        }
        if (lower.startsWith("quan tri vien ")) {
            return normalized.substring(14).trim();
        }
        return normalized;
    }


    private String resolveTicketResponderDisplayName(SupportTicket ticket) {
        if (ticket.getDentist() != null) {
            return resolveResponderLabel(ticket.getDentist());
        }
        if (ticket.getStaff() != null) {
            return resolveResponderLabel(ticket.getStaff());
        }
        if (ticket.getAppointment() != null && ticket.getAppointment().getDentist() != null) {
            return "Bác sĩ " + safeText(ticket.getAppointment().getDentist().getFullName(),
                    ticket.getAppointment().getDentist().getUser() != null ? ticket.getAppointment().getDentist().getUser().getEmail() : "Chưa phân công");
        }
        return "Chưa phân công";
    }

    private String buildServiceLabel(Appointment appointment) {
        if (appointment == null) {
            return "-";
        }

        List<AppointmentDetail> details = appointment.getAppointmentDetails();
        if (details != null && !details.isEmpty()) {
            String joined = details.stream()
                    .sorted(Comparator.comparing(
                            AppointmentDetail::getDetailOrder,
                            Comparator.nullsLast(Integer::compareTo)
                    ))
                    .map(detail -> {
                        String name = detail.getServiceNameSnapshot();
                        if (name == null || name.isBlank()) {
                            if (detail.getService() != null) {
                                name = detail.getService().getName();
                            }
                        }
                        return name;
                    })
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .collect(Collectors.joining(", "));

            if (!joined.isBlank()) {
                return joined;
            }
        }

        if (appointment.getService() != null && appointment.getService().getName() != null) {
            return appointment.getService().getName();
        }

        return "-";
    }

    private String resolveUserFullName(User user) {
        if (user == null) {
            return "Chưa rõ";
        }
        if (user.getRole() == Role.DENTIST) {
            return dentistProfileRepository.findByUser_Id(user.getId())
                    .map(DentistProfile::getFullName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElseGet(() -> safeText(user.getEmail(), "Chưa rõ"));
        }
        if (user.getRole() == Role.STAFF || user.getRole() == Role.ADMIN) {
            return staffProfileRepository.findByUserId(user.getId())
                    .map(StaffProfile::getFullName)
                    .filter(name -> name != null && !name.isBlank())
                    .orElseGet(() -> safeText(user.getEmail(), "Chưa rõ"));
        }
        return safeText(user.getEmail(), "Chưa rõ");
    }

    private String safeText(String value, String fallback) {
        return value != null && !value.isBlank() ? value : fallback;
    }

    private boolean matchesTicketKeyword(SupportTicket ticket, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }

        String normalizedKeyword = normalizeKeyword(keyword);
        if (normalizedKeyword.isEmpty()) {
            return true;
        }

        return containsKeyword(ticket.getTitle(), normalizedKeyword)
                || containsKeyword(ticket.getLatestCustomerMessage(), normalizedKeyword)
                || containsKeyword(ticket.getLatestStaffReply(), normalizedKeyword)
                || containsKeyword(ticket.getResponderDisplayName(), normalizedKeyword)
                || containsKeyword(ticket.getDisplayStatus(), normalizedKeyword)
                || containsKeyword(ticket.getAppointment() != null ? String.valueOf(ticket.getAppointment().getId()) : "Hỗ trợ khác", normalizedKeyword);
    }

    private boolean containsKeyword(String value, String normalizedKeyword) {
        return value != null && normalizeKeyword(value).contains(normalizedKeyword);
    }

    private String normalizeKeyword(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static final class TranscriptEntry {
        private final String senderType;
        private final String senderLabel;
        private final String content;
        private final LocalDateTime createdAt;

        private TranscriptEntry(String senderType, String senderLabel, String content, LocalDateTime createdAt) {
            this.senderType = senderType;
            this.senderLabel = senderLabel;
            this.content = content;
            this.createdAt = createdAt;
        }
    }
}

