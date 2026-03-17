package com.dentalclinic.service.scheduler;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class AppointmentCleanupTask {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CustomerAppointmentService customerAppointmentService;

    // Chạy mỗi 5 phút một lần
    @Scheduled(fixedRate = 300000)
    public void cleanupExpiredPendingDepositAppointments() {
        // Tìm các đơn PENDING tạo cách đây quá 15 phút (thời gian sống của URL VNPay)
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(15);

        // Query findAllByStatusAndCreatedAtBefore trong Repository
        List<Appointment> expiredApps = appointmentRepository.findAllByStatusAndCreatedAtBefore(
                AppointmentStatus.PENDING_DEPOSIT, threshold);

        for (Appointment app : expiredApps) {
            // Hủy đơn và nhả slot
            customerAppointmentService.cancelAppointmentByStaff(app.getId(), "Tự động hủy do quá thời gian thanh toán.");
        }
    }
}

