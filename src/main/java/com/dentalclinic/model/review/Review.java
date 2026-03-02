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
    @JoinColumn(name = "customer_id")
    private CustomerProfile customer;

    @ManyToOne
    @JoinColumn(name = "dentist_id")
    private DentistProfile dentist;

    private int rating; // 1-5 sao

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String comment;

}
