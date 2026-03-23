package com.dentalclinic.model.promotion;

import com.dentalclinic.model.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "voucher_assignment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_voucher_assignment_voucher_user",
                columnNames = {"voucher_id", "user_id"}
        ),
        indexes = {
                @Index(name = "idx_voucher_assignment_voucher", columnList = "voucher_id"),
                @Index(name = "idx_voucher_assignment_user", columnList = "user_id")
        }
)
public class VoucherAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User customer;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Voucher getVoucher() {
        return voucher;
    }

    public void setVoucher(Voucher voucher) {
        this.voucher = voucher;
    }

    public User getCustomer() {
        return customer;
    }

    public void setCustomer(User customer) {
        this.customer = customer;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(LocalDateTime assignedAt) {
        this.assignedAt = assignedAt;
    }
}
