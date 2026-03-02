package com.dentalclinic.service.support;

import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.exception.SupportAccessDeniedException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.support.SupportStatus;
import com.dentalclinic.model.support.SupportTicket;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.NotificationRepository;
import com.dentalclinic.repository.SupportTicketRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SupportService {

    private final SupportTicketRepository supportTicketRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    public SupportService(SupportTicketRepository supportTicketRepository,
                          NotificationRepository notificationRepository,
                          UserRepository userRepository,
                          AppointmentRepository appointmentRepository) {
        this.supportTicketRepository = supportTicketRepository;
        this.notificationRepository = notificationRepository;
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
        validateTitle(title);
        validateQuestion(question);

        Appointment appointment = null;
        User assignedResponder = null;
        if (appointmentId != null) {
            appointment = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, customerUserId)
                    .orElseThrow(() -> new BusinessException("Không tìm thấy ca khám phù hợp để hỗ trợ."));
            if (appointment.getDentist() != null && appointment.getDentist().getUser() != null) {
                assignedResponder = appointment.getDentist().getUser();
            }
        }

        SupportTicket ticket = new SupportTicket();
        ticket.setCustomer(customer);
        ticket.setAppointment(appointment);
        ticket.setStaff(assignedResponder);
        ticket.setTitle(title.trim());
        ticket.setQuestion(question.trim());
        ticket.setStatus(SupportStatus.OPEN);
        ticket.setCreatedAt(LocalDateTime.now());

        return supportTicketRepository.save(ticket);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentsForSupport(Long customerUserId) {
        requireCustomer(customerUserId);
        return appointmentRepository.findByCustomer_User_IdOrderByDateDesc(customerUserId);
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> getMyTickets(Long customerUserId) {
        requireCustomer(customerUserId);
        return supportTicketRepository.findByCustomer_IdOrderByCreatedAtDesc(customerUserId);
    }

    @Transactional(readOnly = true)
    public Page<SupportTicket> getMyTicketsPage(Long customerUserId, int page, int size) {
        requireCustomer(customerUserId);
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        return supportTicketRepository.findByCustomer_Id(customerUserId, pageable);
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
        return supportTicketRepository.findByStatusOrderByCreatedAtDesc(parseStatus(status));
    }

    @Transactional(readOnly = true)
    public List<SupportTicket> getDentistVisibleTickets(Long dentistUserId, String status) {
        User dentist = requireUser(dentistUserId);
        if (dentist.getRole() != Role.DENTIST) {
            throw new SupportAccessDeniedException("Chỉ bác sĩ mới có quyền xem danh sách này.");
        }

        if (status == null || status.isBlank()) {
            return supportTicketRepository.findVisibleToDentist(dentistUserId);
        }
        return supportTicketRepository.findVisibleToDentistByStatus(dentistUserId, parseStatus(status));
    }

    @Transactional(readOnly = true)
    public SupportTicket getDentistTicketDetail(Long dentistUserId, Long ticketId) {
        User dentist = requireUser(dentistUserId);
        if (dentist.getRole() != Role.DENTIST) {
            throw new SupportAccessDeniedException("Chỉ bác sĩ mới có quyền xem chi tiết phiếu hỗ trợ.");
        }

        return supportTicketRepository.findVisibleToDentistById(ticketId, dentistUserId)
                .orElseThrow(() -> new SupportAccessDeniedException("Bạn không có quyền xem phiếu hỗ trợ này."));
    }

    @Transactional
    public SupportTicket answerTicket(Long responderUserId, Long ticketId, String answer) {
        User responder = requireSupportResponder(responderUserId);
        validateAnswer(answer);

        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (ticket.getStatus() == SupportStatus.CLOSED) {
            throw new BusinessException("Không thể trả lời yêu cầu đã đóng.");
        }

        if (responder.getRole() == Role.DENTIST) {
            validateDentistCanAnswer(responder.getId(), ticket);
        }

        ticket.setStaff(responder);
        ticket.setAnswer(answer.trim());
        ticket.setStatus(SupportStatus.ANSWERED);
        SupportTicket saved = supportTicketRepository.save(ticket);

        // Send Notification
        Notification notification = new Notification();
        notification.setUser(saved.getCustomer());
        notification.setTitle("Phiếu hỗ trợ đã được phản hồi");
        notification.setContent("Yêu cầu hỗ trợ của bạn đã được phản hồi.");
        notification.setType("SUPPORT");
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(notification);

        return saved;
    }

    @Transactional
    public SupportTicket closeTicket(Long staffUserId, Long ticketId) {
        User staff = requireStaffOrAdmin(staffUserId);
        SupportTicket ticket = supportTicketRepository.findById(ticketId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy yêu cầu hỗ trợ."));

        if (ticket.getStatus() == SupportStatus.CLOSED) {
            throw new BusinessException("Yêu cầu hỗ trợ đã đóng.");
        }
        ticket.setStatus(SupportStatus.CLOSED);
        if (ticket.getStaff() == null) {
            ticket.setStaff(staff);
        }
        return supportTicketRepository.save(ticket);
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
        if (user.getRole() != Role.STAFF && user.getRole() != Role.DENTIST) {
            throw new SupportAccessDeniedException("Chỉ nhân viên hoặc bác sĩ mới được phản hồi phiếu hỗ trợ.");
        }
        return user;
    }

    private void validateDentistCanAnswer(Long dentistUserId, SupportTicket ticket) {
        if (ticket.getAppointment() == null) return;

        if (ticket.getAppointment().getDentist() == null || ticket.getAppointment().getDentist().getUser() == null) {
            throw new SupportAccessDeniedException("Phiếu này chưa gán bác sĩ ca khám, bác sĩ không thể phản hồi.");
        }

        Long assignedDentistUserId = ticket.getAppointment().getDentist().getUser().getId();
        if (!assignedDentistUserId.equals(dentistUserId)) {
            throw new SupportAccessDeniedException("Bạn không có quyền phản hồi phiếu liên quan ca khám này.");
        }
    }

    private SupportStatus parseStatus(String status) {
        try {
            return SupportStatus.valueOf(status.trim().toUpperCase());
        } catch (Exception e) {
            throw new BusinessException("Trạng thái lọc không hợp lệ.");
        }
    }

    private void validateTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new BusinessException("Tiêu đề không được để trống.");
        }
    }

    private void validateQuestion(String question) {
        if (question == null || question.trim().isEmpty()) {
            throw new BusinessException("Câu hỏi không được để trống.");
        }
    }

    private void validateAnswer(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            throw new BusinessException("Nội dung trả lời không được để trống.");
        }
    }
}