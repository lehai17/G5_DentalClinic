package com.dentalclinic.service.chat;

import com.dentalclinic.dto.chat.ChatMessageDto;
import com.dentalclinic.dto.chat.ChatThreadDto;
import com.dentalclinic.model.chat.ChatMessage;
import com.dentalclinic.model.chat.ChatThread;
import com.dentalclinic.model.chat.ChatThreadStatus;
import com.dentalclinic.model.notification.NotificationReferenceType;
import com.dentalclinic.model.notification.NotificationType;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.ChatMessageRepository;
import com.dentalclinic.repository.ChatThreadRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
public class ChatService {

    private static final Set<ChatThreadStatus> ACTIVE_STATUSES = Set.of(ChatThreadStatus.OPEN, ChatThreadStatus.ACTIVE);
    private static final List<Role> STAFF_SIDE_ROLES = List.of(Role.STAFF, Role.ADMIN, Role.DENTIST);
    private static final int MAX_MESSAGE_LENGTH = 1000;

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ChatService(ChatThreadRepository chatThreadRepository,
                       ChatMessageRepository chatMessageRepository,
                       UserRepository userRepository,
                       NotificationService notificationService) {
        this.chatThreadRepository = chatThreadRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Transactional
    public ChatThreadDto createOrGetThread(Long customerId) {
        User customer = getRequiredUser(customerId);
        ensureRole(customer, Role.CUSTOMER);

        ChatThread thread = chatThreadRepository
                .findFirstByCustomer_IdAndStatusInOrderByLastMessageAtDesc(customerId, ACTIVE_STATUSES)
                .orElseGet(() -> {
                    ChatThread newThread = new ChatThread();
                    newThread.setCustomer(customer);
                    newThread.setAssignedStaff(pickAssignableStaff());
                    newThread.setStatus(ChatThreadStatus.OPEN);
                    return chatThreadRepository.save(newThread);
                });

        return toThreadDto(thread, ViewerType.CUSTOMER);
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesForCustomer(Long customerId, Long threadId) {
        ChatThread thread = getThreadForCustomer(customerId, threadId);
        return chatMessageRepository.findByThread_IdOrderByCreatedAtAsc(thread.getId())
                .stream()
                .map(this::toMessageDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesForStaff(Long staffId, Long threadId) {
        User staff = getRequiredUser(staffId);
        ensureStaffRole(staff);
        ChatThread thread = getThread(threadId);
        return chatMessageRepository.findByThread_IdOrderByCreatedAtAsc(thread.getId())
                .stream()
                .map(this::toMessageDto)
                .toList();
    }

    @Transactional
    public ChatMessageDto sendCustomerMessage(Long customerId, Long threadId, String content) {
        User customer = getRequiredUser(customerId);
        ensureRole(customer, Role.CUSTOMER);
        ChatThread thread = getThreadForCustomer(customerId, threadId);
        ensureThreadWritable(thread);

        ChatMessage saved = saveMessage(thread, customer, content);
        if (thread.getAssignedStaff() == null) {
            thread.setAssignedStaff(pickAssignableStaff());
        }
        thread.setStatus(ChatThreadStatus.ACTIVE);
        thread.setLastMessageAt(saved.getCreatedAt());
        chatThreadRepository.save(thread);
        return toMessageDto(saved);
    }

    @Transactional
    public ChatMessageDto sendStaffMessage(Long staffId, Long threadId, String content) {
        User staff = getRequiredUser(staffId);
        ensureStaffRole(staff);
        ChatThread thread = getThread(threadId);
        ensureThreadWritable(thread);

        thread.setAssignedStaff(staff);
        ChatMessage saved = saveMessage(thread, staff, content);
        thread.setStatus(ChatThreadStatus.ACTIVE);
        thread.setLastMessageAt(saved.getCreatedAt());
        chatThreadRepository.save(thread);

        notificationService.createForCustomer(
                thread.getCustomer().getId(),
                NotificationType.SUPPORT,
                "Lễ tân đã phản hồi tin nhắn",
                "Bạn có tin nhắn mới từ lễ tân.",
                "/customer/chat",
                NotificationReferenceType.TICKET,
                thread.getId()
        );
        return toMessageDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ChatThreadDto> getStaffInbox(Long staffId) {
        User staff = getRequiredUser(staffId);
        ensureStaffRole(staff);
        return chatThreadRepository.findByStatusInOrderByLastMessageAtDesc(ACTIVE_STATUSES)
                .stream()
                .map(thread -> toThreadDto(thread, ViewerType.STAFF))
                .sorted(Comparator.comparing(ChatThreadDto::getLastMessageAt).reversed())
                .toList();
    }

    @Transactional
    public int markMessagesReadForCustomer(Long customerId, Long threadId) {
        ChatThread thread = getThreadForCustomer(customerId, threadId);
        return chatMessageRepository.markIncomingMessagesRead(thread.getId(), customerId);
    }

    @Transactional
    public int markMessagesReadForStaff(Long staffId, Long threadId) {
        User staff = getRequiredUser(staffId);
        ensureStaffRole(staff);
        ChatThread thread = getThread(threadId);
        return chatMessageRepository.markIncomingMessagesRead(thread.getId(), staff.getId());
    }

    @Transactional(readOnly = true)
    public long getUnreadCountForCustomer(Long customerId, Long threadId) {
        getThreadForCustomer(customerId, threadId);
        return chatMessageRepository.countByThread_IdAndIsReadFalseAndSender_RoleIn(threadId, STAFF_SIDE_ROLES);
    }

    @Transactional(readOnly = true)
    public User getCurrentUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Không xác định được tài khoản hiện tại.");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
    }

    private ChatMessage saveMessage(ChatThread thread, User sender, String content) {
        String safeContent = content == null ? "" : content.trim();
        if (safeContent.isEmpty()) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống.");
        }
        if (safeContent.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Tin nhắn vượt quá giới hạn cho phép.");
        }

        ChatMessage message = new ChatMessage();
        message.setThread(thread);
        message.setSender(sender);
        message.setContent(safeContent);
        message.setRead(false);
        return chatMessageRepository.save(message);
    }

    private ChatThread getThread(Long threadId) {
        if (threadId == null) {
            throw new IllegalArgumentException("Thiếu mã cuộc hội thoại.");
        }
        return chatThreadRepository.findById(threadId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy cuộc hội thoại."));
    }

    private ChatThread getThreadForCustomer(Long customerId, Long threadId) {
        ChatThread thread = getThread(threadId);
        if (!thread.getCustomer().getId().equals(customerId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập cuộc hội thoại này.");
        }
        return thread;
    }

    private User getRequiredUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy người dùng."));
    }

    private void ensureRole(User user, Role role) {
        if (user.getRole() != role) {
            throw new AccessDeniedException("Người dùng không hợp lệ.");
        }
    }

    private void ensureStaffRole(User user) {
        if (!(user.getRole() == Role.STAFF || user.getRole() == Role.ADMIN || user.getRole() == Role.DENTIST)) {
            throw new AccessDeniedException("Người dùng không có quyền staff.");
        }
    }

    private void ensureThreadWritable(ChatThread thread) {
        if (thread.getStatus() == ChatThreadStatus.CLOSED) {
            throw new IllegalStateException("Cuộc trò chuyện này đã đóng.");
        }
    }

    private User pickAssignableStaff() {
        return userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.STAFF || user.getRole() == Role.ADMIN)
                .findFirst()
                .orElse(null);
    }

    private ChatThreadDto toThreadDto(ChatThread thread, ViewerType viewerType) {
        ChatThreadDto dto = new ChatThreadDto();
        dto.setId(thread.getId());
        dto.setCustomerId(thread.getCustomer() != null ? thread.getCustomer().getId() : null);
        dto.setCustomerEmail(thread.getCustomer() != null ? thread.getCustomer().getEmail() : null);
        dto.setAssignedStaffId(thread.getAssignedStaff() != null ? thread.getAssignedStaff().getId() : null);
        dto.setAssignedStaffEmail(thread.getAssignedStaff() != null ? thread.getAssignedStaff().getEmail() : null);
        dto.setStatus(thread.getStatus().name());
        dto.setCreatedAt(thread.getCreatedAt());
        dto.setLastMessageAt(thread.getLastMessageAt());

        ChatMessage last = chatMessageRepository.findTopByThreadOrderByCreatedAtDesc(thread);
        dto.setLastMessage(last != null ? last.getContent() : "");
        if (viewerType == ViewerType.CUSTOMER && thread.getCustomer() != null) {
            dto.setUnreadCount(chatMessageRepository.countByThread_IdAndIsReadFalseAndSender_RoleIn(
                    thread.getId(), STAFF_SIDE_ROLES
            ));
        } else {
            dto.setUnreadCount(chatMessageRepository.countByThread_IdAndIsReadFalseAndSender_Role(
                    thread.getId(), Role.CUSTOMER
            ));
        }
        return dto;
    }

    private ChatMessageDto toMessageDto(ChatMessage message) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(message.getId());
        dto.setThreadId(message.getThread() != null ? message.getThread().getId() : null);
        dto.setSenderId(message.getSender() != null ? message.getSender().getId() : null);
        dto.setSenderEmail(message.getSender() != null ? message.getSender().getEmail() : null);
        dto.setSenderRole(message.getSender() != null && message.getSender().getRole() != null
                ? message.getSender().getRole().name()
                : null);
        dto.setContent(message.getContent());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setRead(message.isRead());
        return dto;
    }

    private enum ViewerType {
        CUSTOMER,
        STAFF
    }
}
