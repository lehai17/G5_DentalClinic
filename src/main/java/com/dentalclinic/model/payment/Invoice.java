package com.dentalclinic.model.payment;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.promotion.Voucher;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "invoice")
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    @ManyToOne
    @JoinColumn(name = "voucher_id")
    private Voucher voucher;

    @Column(name = "original_amount", precision = 18, scale = 2)
    private BigDecimal originalAmount;

    @Column(name = "discount_amount", precision = 18, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", precision = 18, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "voucher_usage_counted", nullable = false, columnDefinition = "bit default 0")
    private boolean voucherUsageCounted = false;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status = PaymentStatus.UNPAID;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    private void normalizeAmounts() {
        if (discountAmount == null) {
            discountAmount = BigDecimal.ZERO;
        }
        if (originalAmount == null && totalAmount != null) {
            originalAmount = totalAmount;
        }
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Appointment getAppointment() { return appointment; }
    public void setAppointment(Appointment appointment) { this.appointment = appointment; }
    public Voucher getVoucher() { return voucher; }
    public void setVoucher(Voucher voucher) { this.voucher = voucher; }
    public BigDecimal getOriginalAmount() { return originalAmount; }
    public void setOriginalAmount(BigDecimal originalAmount) { this.originalAmount = originalAmount; }
    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public boolean isVoucherUsageCounted() { return voucherUsageCounted; }
    public void setVoucherUsageCounted(boolean voucherUsageCounted) { this.voucherUsageCounted = voucherUsageCounted; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
