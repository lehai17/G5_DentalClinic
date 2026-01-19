package com.dentalclinic.model.profile;

import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "staff_profile")
public class StaffProfile {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    private User user;

    private String position;
}
