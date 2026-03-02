package com.dentalclinic.service.admin;

import com.dentalclinic.dto.admin.CustomerListDTO;
import com.dentalclinic.dto.admin.CustomerDetailDTO;
import com.dentalclinic.dto.admin.CustomerStatDTO;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.user.UserStatus;

import java.util.List;

public interface AdminCustomerService {
    CustomerStatDTO getCustomerStats();

    List<CustomerListDTO> searchCustomers(String keyword, UserStatus status, Long serviceId);

    CustomerDetailDTO getCustomerDetail(Long customerId);

    List<Appointment> getCustomerHistory(Long customerId);

    List<Appointment> getUpcomingAppointments(Long customerId);
}
