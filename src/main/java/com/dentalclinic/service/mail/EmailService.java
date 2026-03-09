package com.dentalclinic.service.mail;

import com.dentalclinic.model.appointment.Appointment;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    @Qualifier("supportMailSender")
    private JavaMailSender supportMailSender;

    @Async
    public void sendAppointmentConfirmed(Appointment appointment) {

        SimpleMailMessage message = new SimpleMailMessage();

        //  FROM PHẢI TRÙNG USERNAME SMTP
        message.setFrom("lenguyendaihai17@gmail.com");

        String toEmail = appointment.getCustomer().getUser().getEmail();
        message.setTo(toEmail);

        message.setSubject("X�c nhận lịch kh�m - GENZ CLINIC");

        message.setText("""
            Xin chào %s,

            Lịch kh�m của bạn d� được x�c nhận thành công.

            🦷 Dịch vụ: %s
            👨‍⚕️ B�c sĩ: %s
            📅 Ngày kh�m: %s
            ⏰ Thời gian: %s - %s

            Tr�n trọng,
            GENZ CLINIC
            """.formatted(
                appointment.getCustomer().getFullName(),
                appointment.getService().getName(),
                appointment.getDentist().getFullName(),
                appointment.getDate(),
                appointment.getStartTime(),
                appointment.getEndTime()
        ));

        supportMailSender.send(message);
    }
}

