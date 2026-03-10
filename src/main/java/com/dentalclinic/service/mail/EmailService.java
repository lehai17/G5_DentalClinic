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

        message.setSubject("Xï¿½c nhận lịch khï¿½m - GENZ CLINIC");

        message.setText("""
            Xin ch� o %s,

            Lịch khï¿½m của bạn dï¿½ được xï¿½c nhận th� nh công.

            ðŸ¦· Dịch vụ: %s
            👨‍⚕️ Bï¿½c sĩ: %s
            ðŸ“… Ng� y khï¿½m: %s
            â° Thời gian: %s - %s

            Trï¿½n trọng,
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


