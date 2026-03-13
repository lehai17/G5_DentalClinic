package com.dentalclinic.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class AdminWalletTransactionRowDTO {
    private final Long id;
    private final String customerName;
    private final String email;
    private final String phone;
    private final String type;
    private final String typeLabel;
    private final String status;
    private final String statusLabel;
    private final BigDecimal amount;
    private final String description;
    private final LocalDateTime createdAt;

    public AdminWalletTransactionRowDTO(Long id,
                                        String customerName,
                                        String email,
                                        String phone,
                                        String type,
                                        String typeLabel,
                                        String status,
                                        String statusLabel,
                                        BigDecimal amount,
                                        String description,
                                        LocalDateTime createdAt) {
        this.id = id;
        this.customerName = customerName;
        this.email = email;
        this.phone = phone;
        this.type = type;
        this.typeLabel = typeLabel;
        this.status = status;
        this.statusLabel = statusLabel;
        this.amount = amount;
        this.description = description;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getType() {
        return type;
    }

    public String getStatus() {
        return status;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public String getStatusLabel() {
        return statusLabel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
