package com.dentalclinic.model.wallet;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallet_transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WalletTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WalletTransactionType type;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private WalletTransactionStatus status = WalletTransactionStatus.COMPLETED;

    @Column(length = 500)
    private String description;

    @Column(name = "appointment_id")
    private Long appointmentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public void setWallet(Wallet wallet) {
        this.wallet = wallet;
    }

    public WalletTransactionType getType() {
        return type;
    }

    public void setType(WalletTransactionType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public WalletTransactionStatus getStatus() {
        return status;
    }

    public void setStatus(WalletTransactionStatus status) {
        this.status = status;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getAppointmentId() {
        return appointmentId;
    }

    public void setAppointmentId(Long appointmentId) {
        this.appointmentId = appointmentId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    // --- BỔ SUNG BUILDER THỦ CÔNG ---
    public static WalletTransactionBuilder builder() {
        return new WalletTransactionBuilder();
    }

    public static class WalletTransactionBuilder {
        private Wallet wallet;
        private WalletTransactionType type;
        private BigDecimal amount;
        private WalletTransactionStatus status = WalletTransactionStatus.COMPLETED;
        private String description;
        private Long appointmentId;

        public WalletTransactionBuilder wallet(Wallet wallet) {
            this.wallet = wallet;
            return this;
        }

        public WalletTransactionBuilder type(WalletTransactionType type) {
            this.type = type;
            return this;
        }

        public WalletTransactionBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public WalletTransactionBuilder status(WalletTransactionStatus status) {
            this.status = status;
            return this;
        }

        public WalletTransactionBuilder description(String description) {
            this.description = description;
            return this;
        }

        public WalletTransactionBuilder appointmentId(Long appointmentId) {
            this.appointmentId = appointmentId;
            return this;
        }

        public WalletTransaction build() {
            WalletTransaction transaction = new WalletTransaction();
            transaction.setWallet(this.wallet);
            transaction.setType(this.type);
            transaction.setAmount(this.amount);
            transaction.setStatus(this.status);
            transaction.setDescription(this.description);
            transaction.setAppointmentId(this.appointmentId);
            return transaction;
        }
    }
}
