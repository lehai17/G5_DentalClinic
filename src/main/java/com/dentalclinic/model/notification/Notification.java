package com.dentalclinic.model.notification;

import com.dentalclinic.model.user.User;
import com.dentalclinic.util.DisplayTextUtils;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

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

    @Column(name = "is_read", nullable = false)
    private boolean isRead;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Transient
    private boolean relatedAvailable = true;

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
        return DisplayTextUtils.normalize(value);
    }

    private String fallbackQuestionMarkTitle(String value) {
        if (value == null || !value.contains("?")) {
            return value;
        }

        if (type == NotificationType.BOOKING_UPDATED) {
            return "L\u1ecbch h\u1eb9n \u0111\u01b0\u1ee3c c\u1eadp nh\u1eadt";
        }
        if (type == NotificationType.BOOKING_CANCELLED) {
            return "L\u1ecbch h\u1eb9n \u0111\u00e3 b\u1ecb h\u1ee7y";
        }
        if (type == NotificationType.BOOKING_CREATED) {
            return "L\u1ecbch h\u1eb9n \u0111\u00e3 \u0111\u01b0\u1ee3c t\u1ea1o";
        }
        if (type == NotificationType.APPOINTMENT_REMINDER) {
            return "Nh\u1eafc l\u1ecbch h\u1eb9n";
        }
        return value;
    }

    private String fallbackQuestionMarkContent(String value) {
        if (value == null || !value.contains("?")) {
            return value;
        }

        String appointmentRef = referenceId != null ? " #" + referenceId : "";
        if (type == NotificationType.BOOKING_UPDATED) {
            return "L\u1ecbch h\u1eb9n" + appointmentRef + " \u0111\u00e3 \u0111\u01b0\u1ee3c c\u1eadp nh\u1eadt.";
        }
        if (type == NotificationType.BOOKING_CANCELLED) {
            return "L\u1ecbch h\u1eb9n" + appointmentRef + " \u0111\u00e3 b\u1ecb h\u1ee7y.";
        }
        if (type == NotificationType.BOOKING_CREATED) {
            return "L\u1ecbch h\u1eb9n" + appointmentRef + " \u0111\u00e3 \u0111\u01b0\u1ee3c t\u1ea1o th\u00e0nh c\u00f4ng.";
        }
        if (type == NotificationType.APPOINTMENT_REMINDER) {
            return "B\u1ea1n c\u00f3 l\u1ecbch h\u1eb9n s\u1eafp t\u1edbi. Vui l\u00f2ng ki\u1ec3m tra l\u1ea1i th\u1eddi gian kh\u00e1m.";
        }
        return value;
    }
}
