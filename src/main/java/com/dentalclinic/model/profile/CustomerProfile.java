package com.dentalclinic.model.profile;

import com.dentalclinic.model.user.User;
import jakarta.persistence.*;

@Entity
@Table(name = "customer_profile")
public class CustomerProfile {

    @Id
    private Long id;

    @OneToOne
    @MapsId
    private User user;

    private String fullName;
    private String phone;
    private String address;
}
