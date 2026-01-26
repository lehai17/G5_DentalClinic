package com.dentalclinic.repository;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.profile.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CustomerProfileRepository extends JpaRepository<CustomerProfile, Long> {

}