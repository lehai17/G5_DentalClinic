package com.dentalclinic.model.profile;

import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "dentist_profile")
public class DentistProfile {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    private User user;

    private String specialization;
    private int experienceYears;
}
