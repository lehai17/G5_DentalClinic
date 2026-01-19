package com.dentalclinic.model.support;

import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "support_ticket")
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "customer_id")
    private User customer;

    @ManyToOne
    @JoinColumn(name = "dentist_id")
    private User dentist;

    private String question;
    private String answer;
    private String status;
    private LocalDateTime createdAt;
}

