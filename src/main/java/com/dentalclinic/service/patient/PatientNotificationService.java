package com.dentalclinic.service.patient;

import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PatientNotificationService {

    private final NotificationRepository notificationRepository;

    public PatientNotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public List<Notification> getNotifications(Long patientUserId) {
        return notificationRepository
                .findByUser_IdOrderByCreatedAtDesc(patientUserId);
    }

    // Mark single notification as read
    public void markAsRead(Long notificationId, Long patientUserId) {
        int updated = notificationRepository
                .markAsRead(notificationId, patientUserId);

        if (updated == 0) {
            throw new IllegalArgumentException("Notification not found or access denied");
        }
    }
}
