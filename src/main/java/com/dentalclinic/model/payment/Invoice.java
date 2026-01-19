package com.dentalclinic.model.payment;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.promotion.Promotion;
import jakarta.persistence.*;

import java.math.BigDecimal;

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
    @JoinColumn(name = "promotion_id")
    private Promotion promotion;

    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;
}

