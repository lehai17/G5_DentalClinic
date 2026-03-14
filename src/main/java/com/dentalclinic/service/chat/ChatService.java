package com.dentalclinic.service.chat;

import com.dentalclinic.dto.chat.ChatAttachmentDownload;
import com.dentalclinic.dto.chat.ChatMessageDto;
import com.dentalclinic.dto.chat.ChatThreadDto;
import com.dentalclinic.model.chat.ChatMessage;
import com.dentalclinic.model.chat.ChatThread;
import com.dentalclinic.model.chat.ChatThreadStatus;
import com.dentalclinic.model.notification.NotificationReferenceType;
import com.dentalclinic.model.notification.NotificationType;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.profile.StaffProfile;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.ChatMessageRepository;
import com.dentalclinic.repository.ChatThreadRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.StaffProfileRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.notification.NotificationService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ChatService {

    private static final Set<ChatThreadStatus> ACTIVE_STATUSES = Set.of(ChatThreadStatus.OPEN, ChatThreadStatus.ACTIVE);
    private static final List<Role> STAFF_SIDE_ROLES = List.of(Role.STAFF, Role.ADMIN, Role.DENTIST);
    private static final int MAX_MESSAGE_LENGTH = 1000;
    private static final long MAX_ATTACHMENT_SIZE = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_ATTACHMENT_EXTENSIONS = Set.of(
            "pdf", "png", "jpg", "jpeg", "webp", "doc", "docx", "xls", "xlsx", "txt", "csv"
    );
    private static final Path CHAT_UPLOAD_DIR = Paths.get("uploads", "chat");

    private final ChatThreadRepository chatThreadRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final CustomerProfileRepository customerProfileRepository;
    private final StaffProfileRepository staffProfileRepository;
    private final DentistProfileRepository dentistProfileRepository;

    public ChatService(ChatThreadRepository chatThreadRepository,
                       ChatMessageRepository chatMessageRepository,
                       UserRepository userRepository,
                       NotificationService notificationService,
                       CustomerProfileRepository customerProfileRepository,
                       StaffProfileRepository staffProfileRepository,
                       DentistProfileRepository dentistProfileRepository) {
        this.chatThreadRepository = chatThreadRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.customerProfileRepository = customerProfileRepository;
        this.staffProfileRepository = staffProfileRepository;
        this.dentistProfileRepository = dentistProfileRepository;
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
                .map(message -> toMessageDto(message, ViewerType.CUSTOMER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessagesForStaff(Long staffId, Long threadId) {
        User staff = getRequiredUser(staffId);
        ensureStaffRole(staff);
        ChatThread thread = getThread(threadId);
        return chatMessageRepository.findByThread_IdOrderByCreatedAtAsc(thread.getId())
                .stream()
                .map(message -> toMessageDto(message, ViewerType.STAFF))
                .toList();
    }

    @Transactional
    public ChatMessageDto sendCustomerMessage(Long customerId, Long threadId, String content) {
        return sendCustomerMessage(customerId, threadId, content, null);
    }

    @Transactional
    public ChatMessageDto sendCustomerMessage(Long customerId, Long threadId, String content, MultipartFile attachment) {
        User customer = getRequiredUser(customerId);
        ensureRole(customer, Role.CUSTOMER);
        ChatThread thread = getThreadForCustomer(customerId, threadId);
        ensureThreadWritable(thread);

        ChatMessage saved = saveMessage(thread, customer, content, attachment);
        if (thread.getAssignedStaff() == null) {
            thread.setAssignedStaff(pickAssignableStaff());
        }
        thread.setStatus(ChatThreadStatus.ACTIVE);
        thread.setLastMessageAt(saved.getCreatedAt());
        chatThreadRepository.save(thread);
        return toMessageDto(saved, ViewerType.CUSTOMER);
    }

    @Transactional
    public ChatMessageDto sendStaffMessage(Long staffId, Long threadId, String content) {
        return sendStaffMessage(staffId, threadId, content, null);
    }

    @Transactional
    public ChatMessageDto sendStaffMessage(Long staffId, Long threadId, String content, MultipartFile attachment) {
        User staff = getRequiredUser(staffId);
        ensureStaffRole(staff);
        ChatThread thread = getThread(threadId);
        ensureThreadWritable(thread);

        thread.setAssignedStaff(staff);
        ChatMessage saved = saveMessage(thread, staff, content, attachment);
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
        return toMessageDto(saved, ViewerType.STAFF);
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

    @Transactional(readOnly = true)
    public ChatAttachmentDownload getAttachmentForCustomer(Long customerId, Long messageId) {
        ChatMessage message = getMessageWithAttachment(messageId);
        if (!message.getThread().getCustomer().getId().equals(customerId)) {
            throw new AccessDeniedException("Bạn không có quyền truy cập tệp đính kèm này.");
        }
        return toAttachmentDownload(message);
    }

    @Transactional(readOnly = true)
    public ChatAttachmentDownload getAttachmentForStaff(Long staffId, Long messageId) {
        User staff = getRequiredUser(staffId);
        ensureStaffRole(staff);
        ChatMessage message = getMessageWithAttachment(messageId);
        return toAttachmentDownload(message);
    }

    private ChatMessage saveMessage(ChatThread thread, User sender, String content, MultipartFile attachment) {
        String safeContent = content == null ? "" : content.trim();
        AttachmentMetadata attachmentMetadata = storeAttachment(attachment);
        if (safeContent.isEmpty() && attachmentMetadata == null) {
            throw new IllegalArgumentException("Nội dung tin nhắn không được để trống.");
        }
        if (safeContent.length() > MAX_MESSAGE_LENGTH) {
            throw new IllegalArgumentException("Tin nhắn vượt quá giới hạn cho phép.");
        }

        ChatMessage message = new ChatMessage();
        message.setThread(thread);
        message.setSender(sender);
        message.setContent(safeContent);
        if (attachmentMetadata != null) {
            message.setAttachmentOriginalName(attachmentMetadata.originalName());
            message.setAttachmentStorageName(attachmentMetadata.storageName());
            message.setAttachmentContentType(attachmentMetadata.contentType());
            message.setAttachmentSize(attachmentMetadata.size());
        }
        message.setRead(false);
        return chatMessageRepository.save(message);
    }

    private AttachmentMetadata storeAttachment(MultipartFile attachment) {
        if (attachment == null || attachment.isEmpty()) {
            return null;
        }
        if (attachment.getSize() > MAX_ATTACHMENT_SIZE) {
            throw new IllegalArgumentException("Tệp đính kèm không được vượt quá 5MB.");
        }

        String originalName = normalizeFileName(attachment.getOriginalFilename());
        String extension = extractExtension(originalName);
        if (extension == null || !ALLOWED_ATTACHMENT_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("Chỉ hỗ trợ tệp PDF, ảnh, Word, Excel, TXT hoặc CSV.");
        }

        String storageName = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(CHAT_UPLOAD_DIR);
            Path target = CHAT_UPLOAD_DIR.resolve(storageName);
            try (InputStream inputStream = attachment.getInputStream()) {
                Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Không thể lưu tệp đính kèm.", ex);
        }

        String contentType = attachment.getContentType();
        if (contentType == null || contentType.isBlank()) {
            contentType = "application/octet-stream";
        }
        return new AttachmentMetadata(originalName, storageName, contentType, attachment.getSize());
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
        dto.setCustomerName(resolveUserDisplayName(thread.getCustomer()));
        dto.setAssignedStaffId(thread.getAssignedStaff() != null ? thread.getAssignedStaff().getId() : null);
        dto.setAssignedStaffEmail(thread.getAssignedStaff() != null ? thread.getAssignedStaff().getEmail() : null);
        dto.setAssignedStaffName(resolveUserDisplayName(thread.getAssignedStaff()));
        dto.setStatus(thread.getStatus().name());
        dto.setCreatedAt(thread.getCreatedAt());
        dto.setLastMessageAt(thread.getLastMessageAt());

        ChatMessage last = chatMessageRepository.findTopByThreadOrderByCreatedAtDesc(thread);
        dto.setLastMessage(buildLastMessagePreview(last));
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

    private ChatMessageDto toMessageDto(ChatMessage message, ViewerType viewerType) {
        ChatMessageDto dto = new ChatMessageDto();
        dto.setId(message.getId());
        dto.setThreadId(message.getThread() != null ? message.getThread().getId() : null);
        dto.setSenderId(message.getSender() != null ? message.getSender().getId() : null);
        dto.setSenderEmail(message.getSender() != null ? message.getSender().getEmail() : null);
        dto.setSenderName(resolveUserDisplayName(message.getSender()));
        dto.setSenderRole(message.getSender() != null && message.getSender().getRole() != null
                ? message.getSender().getRole().name()
                : null);
        dto.setContent(message.getContent());
        dto.setHasAttachment(message.hasAttachment());
        dto.setAttachmentOriginalName(message.getAttachmentOriginalName());
        dto.setAttachmentContentType(message.getAttachmentContentType());
        dto.setAttachmentSize(message.getAttachmentSize());
        if (message.hasAttachment()) {
            dto.setAttachmentDownloadUrl(
                    viewerType == ViewerType.CUSTOMER
                            ? "/customer/chat/attachments/" + message.getId()
                            : "/staff/chat/attachments/" + message.getId()
            );
        }
        dto.setCreatedAt(message.getCreatedAt());
        dto.setRead(message.isRead());
        return dto;
    }

    private String buildLastMessagePreview(ChatMessage message) {
        if (message == null) {
            return "";
        }
        if (hasText(message.getContent())) {
            return message.getContent();
        }
        if (message.hasAttachment()) {
            return "[Tệp đính kèm] " + message.getAttachmentOriginalName();
        }
        return "";
    }

    private ChatMessage getMessageWithAttachment(Long messageId) {
        if (messageId == null) {
            throw new IllegalArgumentException("Thiếu mã tin nhắn.");
        }
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy tin nhắn."));
        if (!message.hasAttachment()) {
            throw new IllegalArgumentException("Tin nhắn này không có tệp đính kèm.");
        }
        return message;
    }

    private ChatAttachmentDownload toAttachmentDownload(ChatMessage message) {
        Path path = CHAT_UPLOAD_DIR.resolve(message.getAttachmentStorageName());
        try {
            Resource resource = new UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new IllegalArgumentException("Tệp đính kèm không còn tồn tại.");
            }
            return new ChatAttachmentDownload(
                    resource,
                    message.getAttachmentOriginalName(),
                    message.getAttachmentContentType(),
                    message.getAttachmentSize() != null ? message.getAttachmentSize() : -1
            );
        } catch (MalformedURLException ex) {
            throw new IllegalStateException("Không thể đọc tệp đính kèm.", ex);
        }
    }

    private String normalizeFileName(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Tệp đính kèm không hợp lệ.");
        }
        String safeName = Path.of(originalFilename).getFileName().toString().trim();
        if (safeName.isBlank()) {
            throw new IllegalArgumentException("Tệp đính kèm không hợp lệ.");
        }
        return safeName;
    }

    private String extractExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(index + 1).toLowerCase();
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return null;
        }
        if (user.getRole() == Role.CUSTOMER) {
            return customerProfileRepository.findByUser_Id(user.getId())
                    .map(CustomerProfile::getFullName)
                    .filter(this::hasText)
                    .orElseGet(() -> fallbackUserLabel(user));
        }
        if (user.getRole() == Role.DENTIST) {
            return dentistProfileRepository.findByUser_Id(user.getId())
                    .map(DentistProfile::getFullName)
                    .filter(this::hasText)
                    .orElseGet(() -> fallbackUserLabel(user));
        }
        if (user.getRole() == Role.STAFF || user.getRole() == Role.ADMIN) {
            return staffProfileRepository.findByUserId(user.getId())
                    .map(StaffProfile::getFullName)
                    .filter(this::hasText)
                    .orElseGet(() -> fallbackUserLabel(user));
        }
        return fallbackUserLabel(user);
    }

    private String fallbackUserLabel(User user) {
        return hasText(user.getEmail()) ? user.getEmail() : "Chưa rõ";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private enum ViewerType {
        CUSTOMER,
        STAFF
    }

    private record AttachmentMetadata(String originalName, String storageName, String contentType, long size) {
    }
}
