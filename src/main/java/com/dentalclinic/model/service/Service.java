package com.dentalclinic.model.service;

import jakarta.persistence.*;

@Entity
@Table(name = "services")
public class Service {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String description;

    private double price;
    private int durationMinutes;

    private boolean active = true;

    // getter / setter
}
