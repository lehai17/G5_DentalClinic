package com.dentalclinic.service.customer;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository profileRepository;
    private final AppointmentRepository appointmentRepository;

    public CustomerProfileService(CustomerProfileRepository profileRepository,
                                  AppointmentRepository appointmentRepository) {
        this.profileRepository = profileRepository;
        this.appointmentRepository = appointmentRepository;
    }

    public CustomerProfile getCurrentCustomerProfile(Long userId) {
        return profileRepository.findById(userId).orElse(null);
    }

    public List<Appointment> getCustomerAppointments(Long customerId) {
        return appointmentRepository.findByCustomerId(customerId);
    }
}