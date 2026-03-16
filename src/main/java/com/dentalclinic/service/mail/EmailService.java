package com.dentalclinic.service.mail;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.user.User;
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

        // FROM phai trung username SMTP
        message.setFrom("lenguyendaihai17@gmail.com");

        String toEmail = appointment.getCustomer().getUser().getEmail();
        message.setTo(toEmail);

        message.setSubject("X?c nh?n l?ch kh?m - GENZ CLINIC");

        message.setText("""
            Xin ch?o %s,,

            L?ch kh?m c?a b?n ?? ???c x?c nh?n th?nh c?ng.

            Dịch vụ: %s
            Bác sĩ: %s
            Ng?y kh?m: %s
            Th?i gian: %s - %s

            Tr?n tr?ng,
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

    @Async
    public void sendWalletPinOtp(User user, String code) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("lenguyendaihai17@gmail.com");
        message.setTo(user.getEmail());
        message.setSubject("Ma xac minh dat lai PIN vi - GENZ CLINIC");
        message.setText("""
            Xin chao,

            Ma OTP dat lai PIN vi cua ban la: %s

            Ma co hieu luc trong 10 phut.
            Neu ban khong thuc hien yeu cau nay, vui long bo qua email.

            Tran trong,
            GENZ CLINIC
            """.formatted(code));

        supportMailSender.send(message);
    }
}


