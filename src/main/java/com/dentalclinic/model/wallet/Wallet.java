package com.dentalclinic.model.wallet;

import com.dentalclinic.model.profile.CustomerProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
