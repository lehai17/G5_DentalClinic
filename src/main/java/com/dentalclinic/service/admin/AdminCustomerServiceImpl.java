package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.CustomerListDTO;
import com.dentalclinic.dto.admin.CustomerDetailDTO;
import com.dentalclinic.dto.admin.CustomerStatDTO;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AdminCustomerServiceImpl implements AdminCustomerService {

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Override
    public CustomerStatDTO getCustomerStats() {
        long totalCustomers = customerProfileRepository.count();

        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0)
                .withNano(0);
        long newCustomersThisMonth = customerProfileRepository.countNewCustomersSince(startOfMonth);

        long customersWithAppointments = customerProfileRepository.countCustomersWithUpcomingAppointments();

        return new CustomerStatDTO(totalCustomers, newCustomersThisMonth, customersWithAppointments);
    }

    @Override
    public List<CustomerListDTO> searchCustomers(String keyword, UserStatus status, Long serviceId) {
        List<CustomerProfile> profiles = customerProfileRepository.searchCustomers(keyword, status, serviceId);
        return profiles.stream().map(p -> new CustomerListDTO(
                p.getId(),
                p.getFullName(),
                p.getUser().getEmail(),
                p.getPhone(),
                p.getUser().getCreatedAt(),
                p.getUser().getStatus())).collect(Collectors.toList());
    }

    @Override
    public CustomerDetailDTO getCustomerDetail(Long customerId) {
        Optional<CustomerProfile> profileOpt = customerProfileRepository.findById(customerId);
        if (profileOpt.isEmpty()) {
            throw new RuntimeException("Customer not found with ID: " + customerId);
        }
        CustomerProfile p = profileOpt.get();
        return new CustomerDetailDTO(
                p.getId(),
                p.getFullName(),
                p.getUser().getEmail(),
                p.getPhone(),
                p.getUser().getCreatedAt(),
                p.getUser().getStatus(),
                p.getUser().getDateOfBirth(),
                p.getUser().getGender(),
                p.getAddress());
    }

    @Override
    public List<Appointment> getCustomerHistory(Long customerId) {
        return appointmentRepository.findCompletedAppointmentsByCustomerId(customerId);
    }

    @Override
    public List<Appointment> getUpcomingAppointments(Long customerId) {
        return appointmentRepository.findUpcomingAppointmentsByCustomerId(
                customerId,
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)
        );
    }
}
