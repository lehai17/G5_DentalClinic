package com.dentalclinic.model.chat;

import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false)
    private ChatThread thread;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Column(name = "content", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String content;

    @Column(name = "attachment_original_name", length = 255)
    private String attachmentOriginalName;

    @Column(name = "attachment_storage_name", length = 255)
    private String attachmentStorageName;

    @Column(name = "attachment_content_type", length = 150)
    private String attachmentContentType;

    @Column(name = "attachment_size")
    private Long attachmentSize;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public ChatThread getThread() {
        return thread;
    }

    public void setThread(ChatThread thread) {
        this.thread = thread;
    }

    public User getSender() {
        return sender;
    }

    public void setSender(User sender) {
        this.sender = sender;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getAttachmentOriginalName() {
        return attachmentOriginalName;
    }

    public void setAttachmentOriginalName(String attachmentOriginalName) {
        this.attachmentOriginalName = attachmentOriginalName;
    }

    public String getAttachmentStorageName() {
        return attachmentStorageName;
    }

    public void setAttachmentStorageName(String attachmentStorageName) {
        this.attachmentStorageName = attachmentStorageName;
    }

    public String getAttachmentContentType() {
        return attachmentContentType;
    }

    public void setAttachmentContentType(String attachmentContentType) {
        this.attachmentContentType = attachmentContentType;
    }

    public Long getAttachmentSize() {
        return attachmentSize;
    }

    public void setAttachmentSize(Long attachmentSize) {
        this.attachmentSize = attachmentSize;
    }

    public boolean hasAttachment() {
        return attachmentStorageName != null && !attachmentStorageName.isBlank();
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }
}
