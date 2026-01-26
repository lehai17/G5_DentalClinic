package com.dentalclinic.service.patient;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.NotificationRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PatientDashboardService {

    private final AppointmentRepository appointmentRepository;
    private final NotificationRepository notificationRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public PatientDashboardService(
            AppointmentRepository appointmentRepository,
            NotificationRepository notificationRepository,
            CustomerProfileRepository customerProfileRepository
    ) {
        this.appointmentRepository = appointmentRepository;
        this.notificationRepository = notificationRepository;
        this.customerProfileRepository = customerProfileRepository;
    }

    // =========================
    // Appointments
    // =========================
    public List<Appointment> getPatientAppointments(Long patientUserId) {
        return appointmentRepository
                .findByCustomer_User_IdOrderByDateDesc(patientUserId);
    }

    // =========================
    // Notifications
    // =========================
    public List<Notification> getNotifications(Long userId) {
        return notificationRepository
                .findByUser_IdOrderByCreatedAtDesc(userId);
    }

    public long countUnreadNotifications(Long userId) {
        return notificationRepository
                .countByUser_IdAndIsReadFalse(userId);
    }

    // =========================
    // Patient profile ✅
    // =========================
    public CustomerProfile getPatientProfile(Long userId) {
        return customerProfileRepository
                .findByUser_Id(userId)
                .orElse(null);
    }
}
