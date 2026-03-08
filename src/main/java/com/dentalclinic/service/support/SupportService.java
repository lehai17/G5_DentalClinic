package com.dentalclinic.service.support;

import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.exception.SupportAccessDeniedException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.notification.NotificationReferenceType;
import com.dentalclinic.model.notification.NotificationType;
import com.dentalclinic.model.support.SupportStatus;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.AppointmentRepository;
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
import java.util.List;

@Service
public class SupportService {

    private static final String TRANSCRIPT_PREFIX = "CHATV2";
    private static final String SENDER_CUSTOMER = "CUSTOMER";
    private static final String SENDER_STAFF = "STAFF";
    private static final int MAX_TITLE_LENGTH = 255;
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final SupportTicketRepository supportTicketRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    public SupportService(SupportTicketRepository supportTicketRepository,
                          NotificationService notificationService,
                          UserRepository userRepository,
                          AppointmentRepository appointmentRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
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
    public SupportTicket createTicket(Long customerUserId, String question) {
        return createTicket(customerUserId, null, "Hỗ trợ chuyên môn", question);
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
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsForSupport(Long customerUserId) {
        requireCustomer(customerUserId);
        return appointmentRepository.findByCustomer_User_IdOrderByDateDesc(customerUserId);
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> getMyTickets(Long customerUserId) {
        requireCustomer(customerUserId);
        return supportTicketRepository.findByCustomer_IdOrderByCreatedAtDesc(customerUserId)
                .stream()
                .map(this::hydrateTicket)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SupportTicket> getMyTicketsPage(Long customerUserId, int page, int size) {
        requireCustomer(customerUserId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return supportTicketRepository.findByCustomer_Id(customerUserId, pageable)
                .map(this::hydrateTicket);
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
        return hydrateTicket(saved);
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
        return customer != null && customer.getEmail() != null ? customer.getEmail() : "Khách hàng";
    }

    private String resolveResponderLabel(User responder) {
        if (responder == null) {
            return "Phòng khám";
        }
        if (responder.getRole() == Role.DENTIST) {
            return "Bác sĩ " + responder.getEmail();
        }
        if (responder.getRole() == Role.ADMIN) {
            return "Quản trị viên " + responder.getEmail();
        }
        return "Lễ tân " + responder.getEmail();
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
