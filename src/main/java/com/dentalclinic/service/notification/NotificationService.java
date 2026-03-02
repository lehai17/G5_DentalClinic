package com.dentalclinic.service.notification;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.notification.Notification;
import com.dentalclinic.model.notification.NotificationReferenceType;
import com.dentalclinic.model.notification.NotificationType;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.NotificationRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
public class NotificationService {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final AppointmentRepository appointmentRepository;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               AppointmentRepository appointmentRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional
    public Notification createForCustomer(Long recipientId,
                                          NotificationType type,
                                          String title,
                                          String message,
                                          String url,
                                          NotificationReferenceType referenceType,
                                          Long referenceId) {
        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found"));
        if (recipient.getRole() != Role.CUSTOMER) {
            throw new IllegalArgumentException("Notification recipient must be CUSTOMER");
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent(message);
        notification.setUrl(url);
        notification.setReferenceType(referenceType);
        notification.setReferenceId(referenceId);
        notification.setRead(false);
        notification.setCreatedAt(LocalDateTime.now(CLINIC_ZONE));
        notification.setReadAt(null);
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getCustomerNotifications(Long recipientId, Pageable pageable) {
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Notification> getCustomerNotifications(Long recipientId, Pageable pageable, boolean unreadOnly) {
        if (unreadOnly) {
            return notificationRepository.findByUser_IdAndIsReadFalseOrderByCreatedAtDesc(recipientId, pageable);
        }
        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(recipientId, pageable);
    }

    @Transactional(readOnly = true)
    public List<Notification> getTopCustomerNotifications(Long recipientId, int limit) {
        List<Notification> top = notificationRepository.findTop5ByUser_IdOrderByCreatedAtDesc(recipientId);
        if (limit >= top.size()) return top;
        return top.subList(0, limit);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long recipientId) {
        return notificationRepository.countByUser_IdAndIsReadFalse(recipientId);
    }

    @Transactional
    public void markRead(Long notificationId, Long recipientId) {
        int updated = notificationRepository.markAsRead(notificationId, recipientId);
        if (updated == 0) {
            throw new IllegalArgumentException("Notification not found or access denied");
        }
    }

    @Transactional
    public int markAllRead(Long recipientId) {
        return notificationRepository.markAllAsRead(recipientId);
    }

    @Transactional(readOnly = true)
    public Notification getOwnedNotification(Long notificationId, Long recipientId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));
        if (!notification.getUser().getId().equals(recipientId)) {
            throw new IllegalArgumentException("Access denied");
        }
        return notification;
    }

    @Transactional
    public void notifyBookingCreated(Appointment appointment) {
        Long recipientId = appointment.getCustomer().getUser().getId();
        String title = "Đặt lịch thành công";
        String message = "Bạn đã tạo lịch hẹn #" + appointment.getId() + " thành công.";
        String url = "/customer/my-appointments#highlight=" + appointment.getId();
        createForCustomer(recipientId, NotificationType.BOOKING_CREATED, title, message, url,
                NotificationReferenceType.APPOINTMENT, appointment.getId());
    }

    @Transactional
    public void notifyBookingUpdated(Appointment appointment, String reason) {
        Long recipientId = appointment.getCustomer().getUser().getId();
        String title = "Lịch hẹn được cập nhật";
        String message = "Lịch hẹn #" + appointment.getId() + " đã được cập nhật"
                + (reason == null || reason.isBlank() ? "." : ": " + reason + ".");
        String url = "/customer/my-appointments#highlight=" + appointment.getId();
        createForCustomer(recipientId, NotificationType.BOOKING_UPDATED, title, message, url,
                NotificationReferenceType.APPOINTMENT, appointment.getId());
    }

    @Transactional
    public void notifyBookingCancelled(Appointment appointment, String reason) {
        Long recipientId = appointment.getCustomer().getUser().getId();
        String title = "Lịch hẹn đã bị hủy";
        String message = "Lịch hẹn #" + appointment.getId() + " đã bị hủy"
                + (reason == null || reason.isBlank() ? "." : ": " + reason + ".");
        String url = "/customer/my-appointments#highlight=" + appointment.getId();
        createForCustomer(recipientId, NotificationType.BOOKING_CANCELLED, title, message, url,
                NotificationReferenceType.APPOINTMENT, appointment.getId());
    }

    @Transactional
    public void notifyMedicalRecordCreated(Long customerUserId, Long recordId) {
        createForCustomer(
                customerUserId,
                NotificationType.MEDICAL_RECORD_CREATED,
                "Có hồ sơ khám mới",
                "Hồ sơ khám mới đã được tạo cho bạn.",
                "/patient/medical-records",
                NotificationReferenceType.RECORD,
                recordId
        );
    }

    @Transactional
    public void notifyPrescriptionCreated(Long customerUserId, Long prescriptionId) {
        createForCustomer(
                customerUserId,
                NotificationType.PRESCRIPTION_CREATED,
                "Có đơn thuốc mới",
                "Bác sĩ đã tạo đơn thuốc mới cho bạn.",
                "/patient/prescriptions",
                NotificationReferenceType.PRESCRIPTION,
                prescriptionId
        );
    }

    @Transactional
    public void notifyFollowupRecommended(Long customerUserId, Long appointmentId) {
        createForCustomer(
                customerUserId,
                NotificationType.FOLLOWUP_RECOMMENDED,
                "Có chỉ định tái khám",
                "Bạn được chỉ định tái khám. Vui lòng kiểm tra lịch hẹn.",
                "/customer/my-appointments#highlight=" + appointmentId,
                NotificationReferenceType.FOLLOWUP,
                appointmentId
        );
    }

    @Scheduled(cron = "0 */30 * * * *", zone = "Asia/Ho_Chi_Minh")
    @Transactional
    public void sendAppointmentReminderNotifications() {
        LocalDateTime now = LocalDateTime.now(CLINIC_ZONE);
        LocalDate targetDate = now.plusHours(24).toLocalDate();

        List<Appointment> appointments = appointmentRepository.findByDateAndStatusIn(
                targetDate,
                List.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED)
        );

        for (Appointment appointment : appointments) {
            if (appointment.getCustomer() == null || appointment.getCustomer().getUser() == null) continue;
            LocalDateTime start = LocalDateTime.of(appointment.getDate(), appointment.getStartTime());
            long minutesDiff = java.time.Duration.between(now, start).toMinutes();
            if (minutesDiff < 23 * 60 || minutesDiff > 25 * 60) continue;

            Long recipientId = appointment.getCustomer().getUser().getId();
            boolean sent = notificationRepository.existsByUser_IdAndTypeAndReferenceTypeAndReferenceId(
                    recipientId,
                    NotificationType.APPOINTMENT_REMINDER,
                    NotificationReferenceType.APPOINTMENT,
                    appointment.getId()
            );
            if (sent) continue;

            String title = "Nhắc lịch hẹn";
            String message = "Bạn có lịch hẹn #" + appointment.getId() + " vào ngày "
                    + appointment.getDate() + " lúc " + appointment.getStartTime() + ".";
            String url = "/customer/my-appointments#highlight=" + appointment.getId();
            createForCustomer(recipientId, NotificationType.APPOINTMENT_REMINDER, title, message, url,
                    NotificationReferenceType.APPOINTMENT, appointment.getId());
        }
    }
}
