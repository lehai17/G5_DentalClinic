package com.dentalclinic.model.review;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import jakarta.persistence.*;

@Entity
@Table(name = "review")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private CustomerProfile customer;

    @ManyToOne
    private DentistProfile dentist;

    private int rating;
    private String comment;
}
