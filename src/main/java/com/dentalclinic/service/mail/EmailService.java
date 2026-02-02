package com.dentalclinic.service.mail;

import com.dentalclinic.model.appointment.Appointment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    // ✅ MAIL THẬT – dùng khi CONFIRM
    @Async
    public void sendAppointmentConfirmed(Appointment appointment) {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("lenguyendaihai17@gmail.com");

        String toEmail = appointment.getCustomer() != null &&
                appointment.getCustomer().getUser() != null
                ? appointment.getCustomer().getUser().getEmail()
                : null;

        if (toEmail == null || toEmail.isBlank()) {
            throw new RuntimeException("Customer email not found");
        }

        message.setTo(toEmail);


        message.setSubject("Xác nhận lịch khám - Dental Clinic");

        message.setText("""
            Xin chào %s,

            Lịch khám của bạn đã được xác nhận thành công.

            🦷 Dịch vụ: %s
            👨‍⚕️ Bác sĩ: %s
            📅 Ngày khám: %s
            ⏰ Thời gian: %s - %s

            Vui lòng đến trước 10 phút.

            Trân trọng,
            Dental Clinic
            """.formatted(
                appointment.getCustomer().getFullName(),
                appointment.getService().getName(),
                appointment.getDentist().getFullName(),
                appointment.getDate(),
                appointment.getStartTime(),
                appointment.getEndTime()
        ));

        mailSender.send(message);
    }

    // 🧪 MAIL TEST (giữ lại để debug)
    @Async
    public void sendTestMail() {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("lenguyendaihai17@gmail.com");
        message.setTo("hailndhe182237@fpt.edu.vn");
        message.setSubject("Test gửi mail từ DentalClinic");
        message.setText("Test mail OK");

        mailSender.send(message);
    }
}
