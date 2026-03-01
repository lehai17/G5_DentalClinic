package com.dentalclinic.service.support;

import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.exception.SupportAccessDeniedException;
import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.NotificationRepository;
import com.dentalclinic.repository.SupportTicketRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SupportService {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_ANSWERED = "ANSWERED";
    public static final String STATUS_CLOSED = "CLOSED";

    private final SupportTicketRepository supportTicketRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public SupportService(SupportTicketRepository supportTicketRepository,
                          NotificationRepository notificationRepository,
                          UserRepository userRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public SupportTicket createTicket(Long customerUserId, String question) {
        User customer = requireUser(customerUserId);
        if (customer.getRole() != Role.CUSTOMER) {
            throw new SupportAccessDeniedException("Chỉ khách hàng mới được tạo yêu cầu hỗ trợ.");
        }
        validateQuestion(question);

        SupportTicket ticket = new SupportTicket();
        ticket.setCustomer(customer);
        ticket.setQuestion(question.trim());
        ticket.setAnswer(null);
        ticket.setStatus(STATUS_OPEN);
        ticket.setStaff(null);
        ticket.setCreatedAt(LocalDateTime.now());
        return supportTicketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> getMyTickets(Long customerUserId) {
        User customer = requireUser(customerUserId);
        if (customer.getRole() != Role.CUSTOMER) {
            throw new SupportAccessDeniedException("Chỉ khách hàng mới được xem danh sách phiếu hỗ trợ của mình.");
        }
        return supportTicketRepository.findByCustomer_IdOrderByCreatedAtDesc(customerUserId);
    }

    @Transactional(readOnly = true)
    public SupportTicket getTicketDetail(Long userId, Long ticketId) {
        User user = requireUser(userId);
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (user.getRole() == Role.CUSTOMER && !ticket.getCustomer().getId().equals(userId)) {
            throw new SupportAccessDeniedException("Bạn không có quyền xem yêu cầu hỗ trợ này.");
        }
        return ticket;
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> getAllTickets(String status, Long staffUserId) {
        User staff = requireUser(staffUserId);
        if (staff.getRole() != Role.STAFF && staff.getRole() != Role.ADMIN) {
            throw new SupportAccessDeniedException("Bạn không có quyền xem danh sách hỗ trợ.");
        }

        if (status == null || status.isBlank()) {
            return supportTicketRepository.findAllByOrderByCreatedAtDesc();
        }

        String normalized = status.trim().toUpperCase();
        if (!STATUS_OPEN.equals(normalized) && !STATUS_ANSWERED.equals(normalized) && !STATUS_CLOSED.equals(normalized)) {
            throw new BusinessException("Trạng thái lọc không hợp lệ.");
        }
        return supportTicketRepository.findByStatusOrderByCreatedAtDesc(normalized);
    }

    @Transactional
    public SupportTicket answerTicket(Long staffUserId, Long ticketId, String answer) {
        User staff = requireUser(staffUserId);
        if (staff.getRole() != Role.STAFF && staff.getRole() != Role.ADMIN) {
            throw new SupportAccessDeniedException("Bạn không có quyền trả lời yêu cầu hỗ trợ.");
        }
        validateAnswer(answer);

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (STATUS_CLOSED.equalsIgnoreCase(ticket.getStatus())) {
            throw new BusinessException("Không thể trả lời yêu cầu đã đóng.");
        }
        if (ticket.getStaff() != null) {
            throw new BusinessException("Yêu cầu này đã được phân công cho nhân viên khác.");
        }
        if (ticket.getAnswer() != null && !ticket.getAnswer().trim().isEmpty()) {
            throw new BusinessException("Yêu cầu này đã được trả lời.");
        }

        ticket.setStaff(staff);
        ticket.setAnswer(answer.trim());
        ticket.setStatus(STATUS_ANSWERED);
        SupportTicket saved = supportTicketRepository.save(ticket);

        Notification notification = new Notification();
        notification.setUser(saved.getCustomer());
        notification.setTitle("Ticket hỗ trợ đã được phản hồi");
        notification.setContent("Yêu cầu hỗ trợ của bạn đã được phản hồi.");
        notification.setType("SUPPORT");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        return saved;
    }

    @Transactional
    public SupportTicket closeTicket(Long staffUserId, Long ticketId) {
        User staff = requireUser(staffUserId);
        if (staff.getRole() != Role.STAFF && staff.getRole() != Role.ADMIN) {
            throw new SupportAccessDeniedException("Bạn không có quyền đóng yêu cầu hỗ trợ.");
        }

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (STATUS_CLOSED.equalsIgnoreCase(ticket.getStatus())) {
            throw new BusinessException("Yêu cầu hỗ trợ đã đóng.");
        }

        ticket.setStatus(STATUS_CLOSED);
        if (ticket.getStaff() == null) {
            ticket.setStaff(staff);
        }
        return supportTicketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public User getCurrentUser(UserDetails principal) {
        if (principal == null || principal.getUsername() == null || principal.getUsername().isBlank()) {
            throw new SupportAccessDeniedException("Không xác định được tài khoản hiện tại.");
        }
        return userRepository.findByEmail(principal.getUsername())
                .orElseThrow(() -> new SupportAccessDeniedException("Không tìm thấy tài khoản hiện tại."));
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy người dùng hiện tại."));
    }

    private void validateQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException("Nội dung câu hỏi không được để trống.");
        }
    }

    private void validateAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new BusinessException("Nội dung trả lời không được để trống.");
        }
    }
}
