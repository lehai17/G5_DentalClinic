package com.dentalclinic.model.wallet;

import com.dentalclinic.model.profile.CustomerProfile;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "wallets")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Wallet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false, unique = true)
    private CustomerProfile customer;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "pin_code", length = 255)
    private String pinCode;

    @Column(name = "pin_failed_attempts", nullable = false)
    private int pinFailedAttempts = 0;

    @Column(name = "pin_locked_until")
    private LocalDateTime pinLockedUntil;

    @Column(name = "pin_reset_otp_hash", length = 255)
    private String pinResetOtpHash;

    @Column(name = "pin_reset_otp_expires_at")
    private LocalDateTime pinResetOtpExpiresAt;

    @Column(name = "pin_reset_verified_until")
    private LocalDateTime pinResetVerifiedUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public CustomerProfile getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerProfile customer) {
        this.customer = customer;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPinCode() {
        return pinCode;
    }

    public void setPinCode(String pinCode) {
        this.pinCode = pinCode;
    }

    public int getPinFailedAttempts() {
        return pinFailedAttempts;
    }

    public void setPinFailedAttempts(int pinFailedAttempts) {
        this.pinFailedAttempts = pinFailedAttempts;
    }

    public LocalDateTime getPinLockedUntil() {
        return pinLockedUntil;
    }

    public void setPinLockedUntil(LocalDateTime pinLockedUntil) {
        this.pinLockedUntil = pinLockedUntil;
    }

    public String getPinResetOtpHash() {
        return pinResetOtpHash;
    }

    public void setPinResetOtpHash(String pinResetOtpHash) {
        this.pinResetOtpHash = pinResetOtpHash;
    }

    public LocalDateTime getPinResetOtpExpiresAt() {
        return pinResetOtpExpiresAt;
    }

    public void setPinResetOtpExpiresAt(LocalDateTime pinResetOtpExpiresAt) {
        this.pinResetOtpExpiresAt = pinResetOtpExpiresAt;
    }

    public LocalDateTime getPinResetVerifiedUntil() {
        return pinResetVerifiedUntil;
    }

    public void setPinResetVerifiedUntil(LocalDateTime pinResetVerifiedUntil) {
        this.pinResetVerifiedUntil = pinResetVerifiedUntil;
    }

    public static WalletBuilder builder() {
        return new WalletBuilder();
    }

    public static class WalletBuilder {
        private CustomerProfile customer;
        private BigDecimal balance = BigDecimal.ZERO;

        public WalletBuilder customer(CustomerProfile customer) {
            this.customer = customer;
            return this;
        }

        public WalletBuilder balance(BigDecimal balance) {
            this.balance = balance;
            return this;
        }

        public Wallet build() {
            Wallet wallet = new Wallet();
            wallet.setCustomer(this.customer);
            wallet.setBalance(this.balance);
            return wallet;
        }
    }
}
