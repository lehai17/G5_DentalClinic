package com.dentalclinic.service.mail;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.service.Services;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.AppointmentRepository;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private AppointmentRepository appointmentRepository;

    @Mock
    private SpringTemplateEngine templateEngine;

    private EmailService emailService;

    @BeforeEach
    void setUp() {
        emailService = new EmailService(
                mailSender,
                appointmentRepository,
                templateEngine,
                "mailer@genzclinic.vn",
                "app-password",
                "mailer@genzclinic.vn",
                "GenZ Clinic",
                true,
                "GenZ Clinic",
                "123 Demo Street",
                "0900000000",
                "contact@genzclinic.vn"
        );
    }

    @Test
    void shouldSendConfirmationEmailAndMarkAppointment() {
        Appointment appointment = buildAppointment("customer@example.com");
        MimeMessage mimeMessage = new MimeMessage(Session.getInstance(new Properties()));

        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));
        when(templateEngine.process(any(String.class), any(Context.class))).thenReturn("<html>ok</html>");
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        boolean sent = emailService.sendAppointmentConfirmationIfNeeded(appointment.getId());

        assertTrue(sent);
        verify(mailSender).send(mimeMessage);
        verify(appointmentRepository).save(argThat(hasConfirmationFlag()));
    }

    @Test
    void shouldSkipDuplicateConfirmationEmail() {
        Appointment appointment = buildAppointment("customer@example.com");
        appointment.setConfirmationEmailSent(true);

        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));

        boolean sent = emailService.sendAppointmentConfirmationIfNeeded(appointment.getId());

        assertFalse(sent);
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    @Test
    void shouldSkipWhenCustomerEmailMissing() {
        Appointment appointment = buildAppointment(null);

        when(appointmentRepository.findByIdForUpdate(appointment.getId())).thenReturn(Optional.of(appointment));

        boolean sent = emailService.sendAppointmentConfirmationIfNeeded(appointment.getId());

        assertFalse(sent);
        verify(mailSender, never()).send(any(MimeMessage.class));
        verify(appointmentRepository, never()).save(any(Appointment.class));
    }

    private Appointment buildAppointment(String email) {
        User user = new User();
        user.setEmail(email);

        CustomerProfile customer = new CustomerProfile();
        customer.setFullName("Nguyen Van A");
        customer.setUser(user);

        Services service = new Services();
        service.setName("Lấy cao răng");

        DentistProfile dentist = new DentistProfile();
        dentist.setFullName("Bac si Minh");

        Appointment appointment = new Appointment();
        appointment.setId(99L);
        appointment.setCustomer(customer);
        appointment.setService(service);
        appointment.setDentist(dentist);
        appointment.setDate(LocalDate.of(2026, 3, 20));
        appointment.setStartTime(LocalTime.of(9, 0));
        appointment.setEndTime(LocalTime.of(9, 30));
        appointment.setDepositAmount(new BigDecimal("250000"));
        appointment.setStatus(AppointmentStatus.PENDING);
        appointment.setContactChannel("EMAIL");
        appointment.setContactValue(email);
        return appointment;
    }

    private ArgumentMatcher<Appointment> hasConfirmationFlag() {
        return appointment -> appointment.isConfirmationEmailSent()
                && appointment.getConfirmationEmailSentAt() != null;
    }
}
