package com.dentalclinic.model.support;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "support_ticket")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne
    @JoinColumn(name = "staff_id")
    private User staff;

    @Column(name = "title", columnDefinition = "NVARCHAR(255)")
    private String title;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String question;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String answer;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    private SupportStatus status = SupportStatus.OPEN;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Transient
    private String customerDisplayName;

    @Transient
    private String responderDisplayName;

    @Transient
    private String latestCustomerMessage;

    @Transient
    private String latestStaffReply;

    @Transient
    private String displayStatus;

    @Transient
    private String serviceLabel;

    @Transient
    private List<ConversationEntry> conversationEntries = new ArrayList<>();

    public SupportTicket() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public void setAppointment(Appointment appointment) {
        this.appointment = appointment;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public User getStaff() {
        return staff;
    }

    public void setStaff(User staff) {
        this.staff = staff;
    }

    // Alias for old dentist naming in other flows
    public User getDentist() {
        return staff;
    }

    public void setDentist(User dentist) {
        this.staff = dentist;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public SupportStatus getStatus() {
        return status;
    }

    public void setStatus(SupportStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getCustomerDisplayName() {
        return customerDisplayName;
    }

    public void setCustomerDisplayName(String customerDisplayName) {
        this.customerDisplayName = customerDisplayName;
    }

    public String getResponderDisplayName() {
        return responderDisplayName;
    }

    public void setResponderDisplayName(String responderDisplayName) {
        this.responderDisplayName = responderDisplayName;
    }

    public String getLatestCustomerMessage() {
        return latestCustomerMessage;
    }

    public void setLatestCustomerMessage(String latestCustomerMessage) {
        this.latestCustomerMessage = latestCustomerMessage;
    }

    public String getLatestStaffReply() {
        return latestStaffReply;
    }

    public void setLatestStaffReply(String latestStaffReply) {
        this.latestStaffReply = latestStaffReply;
    }

    public String getDisplayStatus() {
        return displayStatus;
    }

    public void setDisplayStatus(String displayStatus) {
        this.displayStatus = displayStatus;
    }

    public String getServiceLabel() {
        return serviceLabel;
    }

    public void setServiceLabel(String serviceLabel) {
        this.serviceLabel = serviceLabel;
    }

    public List<ConversationEntry> getConversationEntries() {
        return conversationEntries;
    }

    public void setConversationEntries(List<ConversationEntry> conversationEntries) {
        this.conversationEntries = conversationEntries == null ? new ArrayList<>() : conversationEntries;
    }

    public boolean isClosed() {
        return "CLOSED".equalsIgnoreCase(displayStatus);
    }

    public static class ConversationEntry {
        private String senderType;
        private String senderLabel;
        private String content;
        private LocalDateTime timestamp;
        private boolean customer;

        public ConversationEntry(String senderType, String senderLabel, String content, LocalDateTime timestamp, boolean customer) {
            this.senderType = senderType;
            this.senderLabel = senderLabel;
            this.content = content;
            this.timestamp = timestamp;
            this.customer = customer;
        }

        public String getSenderType() {
            return senderType;
        }

        public void setSenderType(String senderType) {
            this.senderType = senderType;
        }

        public String getSenderLabel() {
            return senderLabel;
        }

        public void setSenderLabel(String senderLabel) {
            this.senderLabel = senderLabel;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public boolean isCustomer() {
            return customer;
        }

        public void setCustomer(boolean customer) {
            this.customer = customer;
        }
    }
}
