package com.dentalclinic.model.promotion;

import jakarta.persistence.*;

@Entity
@Table(name = "promotion")
public class Promotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String code;

    @Column(name = "discount_percent")
    private int discountPercent;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    private boolean active = true;

}