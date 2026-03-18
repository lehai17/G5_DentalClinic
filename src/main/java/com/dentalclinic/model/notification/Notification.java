package com.dentalclinic.model.notification;

import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // user_id
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "title", columnDefinition = "NVARCHAR(255)")
    private String title;

    @Column(name = "content", columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private NotificationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", length = 50)
    private NotificationReferenceType referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    @Column(name = "url", length = 500)
    private String url;

    // is_read
    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    // created_at
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Transient
    private boolean relatedAvailable = true;

    // =========================
    // Getter & Setter
    // =========================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return fallbackQuestionMarkTitle(normalizeDisplayText(title));
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return fallbackQuestionMarkContent(normalizeDisplayText(content));
    }

    public void setContent(String content) {
        this.content = content;
    }

    public NotificationType getType() {
        return type;
    }

    public void setType(NotificationType type) {
        this.type = type;
    }

    public NotificationReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(NotificationReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Long referenceId) {
        this.referenceId = referenceId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt) {
        this.readAt = readAt;
    }

    public boolean isRelatedAvailable() {
        return relatedAvailable;
    }

    public void setRelatedAvailable(boolean relatedAvailable) {
        this.relatedAvailable = relatedAvailable;
    }

    private String normalizeDisplayText(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        if (!looksMojibake(value)) {
            return value;
        }
        String repaired = new String(value.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
        return repaired.isBlank() ? value : repaired;
    }

    private boolean looksMojibake(String value) {
        return value.contains("Ãƒ")
                || value.contains("Ã„")
                || value.contains("Ã¡Â»")
                || value.contains("Ã¡Âº")
                || value.contains("Ã†")
                || value.contains("Ã‚");
    }
    private String fallbackQuestionMarkTitle(String value) {
        if (value == null || !value.contains("?")) {
            return value;
        }

        if (type == NotificationType.BOOKING_UPDATED) {
            return "Lịch hẹn được cập nhật";
        }
        if (type == NotificationType.BOOKING_CANCELLED) {
            return "Lịch hẹn đã bị hủy";
        }
        if (type == NotificationType.BOOKING_CREATED) {
            return "Lịch hẹn đã được tạo";
        }
        if (type == NotificationType.APPOINTMENT_REMINDER) {
            return "Nhắc lịch hẹn";
        }
        return value;
    }

    private String fallbackQuestionMarkContent(String value) {
        if (value == null || !value.contains("?")) {
            return value;
        }

        String appointmentRef = referenceId != null ? " #" + referenceId : "";
        if (type == NotificationType.BOOKING_UPDATED) {
            return "Lịch hẹn" + appointmentRef + " đã được cập nhật.";
        }
        if (type == NotificationType.BOOKING_CANCELLED) {
            return "Lịch hẹn" + appointmentRef + " đã bị hủy.";
        }
        if (type == NotificationType.BOOKING_CREATED) {
            return "Lịch hẹn" + appointmentRef + " đã được tạo thành công.";
        }
        if (type == NotificationType.APPOINTMENT_REMINDER) {
            return "Bạn có lịch hẹn sắp tới. Vui lòng kiểm tra lại thời gian khám.";
        }
        return value;
    }
}
