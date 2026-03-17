package com.dentalclinic.service.mail;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.model.payment.BillingPerformedService;
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.PaymentStatus;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
import com.dentalclinic.repository.InvoiceRepository;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi-VN");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final JavaMailSender supportMailSender;
    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final BillingNoteRepository billingNoteRepository;
    private final SpringTemplateEngine templateEngine;
    private final String smtpUsername;
    private final String smtpPassword;
    private final String fromAddress;
    private final String fromName;
    private final String clinicName;
    private final String clinicAddress;
    private final String clinicPhone;
    private final String clinicEmail;
    private final boolean mailEnabled;

    public EmailService(@Qualifier("supportMailSender") JavaMailSender supportMailSender,
                        AppointmentRepository appointmentRepository,
                        InvoiceRepository invoiceRepository,
                        BillingNoteRepository billingNoteRepository,
                        SpringTemplateEngine templateEngine,
                        @Value("${mail.support.username:${spring.mail.username:}}") String smtpUsername,
                        @Value("${mail.support.password:${spring.mail.password:}}") String smtpPassword,
                        @Value("${mail.from:}") String fromAddress,
                        @Value("${mail.from-name:GenZ Clinic}") String fromName,
                        @Value("${mail.enabled:true}") boolean mailEnabled,
                        @Value("${clinic.name:GenZ Clinic}") String clinicName,
                        @Value("${clinic.address:}") String clinicAddress,
                        @Value("${clinic.phone:}") String clinicPhone,
                        @Value("${clinic.email:}") String clinicEmail) {
        this.supportMailSender = supportMailSender;
        this.appointmentRepository = appointmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.billingNoteRepository = billingNoteRepository;
        this.templateEngine = templateEngine;
        this.smtpUsername = smtpUsername;
        this.smtpPassword = smtpPassword;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.mailEnabled = mailEnabled;
        this.clinicName = clinicName;
        this.clinicAddress = clinicAddress;
        this.clinicPhone = clinicPhone;
        this.clinicEmail = clinicEmail;
    }

    @Transactional
    public boolean sendAppointmentConfirmationIfNeeded(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId).orElse(null);
        if (appointment == null) {
            log.warn("Skip appointment confirmation email because appointment {} does not exist", appointmentId);
            return false;
        }

        if (appointment.isConfirmationEmailSent()) {
            log.info("Skip duplicate appointment confirmation email for appointment {}", appointmentId);
            return false;
        }

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            log.info("Skip appointment confirmation email for cancelled appointment {}", appointmentId);
            return false;
        }

        String recipientEmail = resolveRecipientEmail(appointment);
        if (!isValidEmail(recipientEmail)) {
            log.warn("Skip appointment confirmation email for appointment {} because customer email is missing or invalid", appointmentId);
            return false;
        }

        if (!isMailConfigured()) {
            log.warn("Skip appointment confirmation email for appointment {} because SMTP is not configured", appointmentId);
            return false;
        }

        try {
            sendHtmlMail(
                    recipientEmail,
                    "Xác nhận lịch hẹn #" + appointment.getId() + " - " + clinicName,
                    "email/appointment-confirmation",
                    buildAppointmentConfirmationContext(appointment)
            );

            appointment.setConfirmationEmailSent(true);
            appointment.setConfirmationEmailSentAt(LocalDateTime.now());
            appointmentRepository.save(appointment);

            log.info("Sent appointment confirmation email for appointment {} to {}", appointmentId, recipientEmail);
            return true;
        } catch (MailException ex) {
            log.warn("Failed to send appointment confirmation email for appointment {}", appointmentId, ex);
            return false;
        } catch (Exception ex) {
            log.error("Unexpected error while sending appointment confirmation email for appointment {}", appointmentId, ex);
            return false;
        }
    }

    @Transactional
    public boolean sendAppointmentCompletionIfNeeded(Long appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId).orElse(null);
        if (appointment == null) {
            log.warn("Skip appointment completion email because appointment {} does not exist", appointmentId);
            return false;
        }

        if (appointment.isCompletionEmailSent()) {
            log.info("Skip duplicate appointment completion email for appointment {}", appointmentId);
            return false;
        }

        if (appointment.getStatus() != AppointmentStatus.COMPLETED) {
            log.info("Skip appointment completion email because appointment {} is not completed", appointmentId);
            return false;
        }

        String recipientEmail = resolveRecipientEmail(appointment);
        if (!isValidEmail(recipientEmail)) {
            log.warn("Skip appointment completion email for appointment {} because customer email is missing or invalid", appointmentId);
            return false;
        }

        if (!isMailConfigured()) {
            log.warn("Skip appointment completion email for appointment {} because SMTP is not configured", appointmentId);
            return false;
        }

        try {
            Invoice invoice = invoiceRepository.findByAppointment_Id(appointmentId).orElse(null);
            BillingNote billingNote = billingNoteRepository.findByAppointment_IdWithPerformedServices(appointmentId).orElse(null);

            sendHtmlMail(
                    recipientEmail,
                    "Hóa đơn thanh toán #" + (invoice != null ? invoice.getId() : appointment.getId()) + " - " + clinicName,
                    "email/appointment-completed",
                    buildAppointmentCompletionContext(appointment, invoice, billingNote)
            );

            appointment.setCompletionEmailSent(true);
            appointment.setCompletionEmailSentAt(LocalDateTime.now());
            appointmentRepository.save(appointment);

            log.info("Sent appointment completion email for appointment {} to {}", appointmentId, recipientEmail);
            return true;
        } catch (MailException ex) {
            log.warn("Failed to send appointment completion email for appointment {}", appointmentId, ex);
            return false;
        } catch (Exception ex) {
            log.error("Unexpected error while sending appointment completion email for appointment {}", appointmentId, ex);
            return false;
        }
    }

    @Async
    public void sendWalletPinOtp(User user, String code) {
        if (user == null || !isValidEmail(user.getEmail())) {
            log.warn("Skip wallet PIN OTP email because recipient email is missing or invalid");
            return;
        }

        if (!isMailConfigured()) {
            log.warn("Skip wallet PIN OTP email because SMTP is not configured");
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(resolveFromAddress());
            message.setTo(user.getEmail());
            message.setSubject("Mã xác minh đặt lại PIN ví - " + clinicName);
            message.setText("""
                    Xin chào,

                    Mã OTP đặt lại PIN ví của bạn là: %s

                    Mã có hiệu lực trong 10 phút.
                    Nếu bạn không thực hiện yêu cầu này, vui lòng bỏ qua email.

                    Trân trọng,
                    %s
                    """.formatted(code, clinicName));

            supportMailSender.send(message);
            log.info("Sent wallet PIN OTP email to {}", user.getEmail());
        } catch (MailException ex) {
            log.warn("Failed to send wallet PIN OTP email to {}", user.getEmail(), ex);
        }
    }

    private Context buildAppointmentConfirmationContext(Appointment appointment) {
        Context context = new Context(VIETNAMESE);
        fillCommonClinicContext(context);
        context.setVariable("customerName", resolveCustomerName(appointment));
        context.setVariable("appointmentId", appointment.getId());
        context.setVariable("appointmentDate", appointment.getDate() != null ? appointment.getDate().format(DATE_FORMATTER) : "Chưa xác định");
        context.setVariable("appointmentTime", buildTimeRange(appointment));
        context.setVariable("serviceSummary", buildServiceSummary(appointment));
        context.setVariable("dentistName", appointment.getDentist() != null ? appointment.getDentist().getFullName() : "Sẽ được cập nhật sau");
        context.setVariable("depositAmount", formatCurrency(appointment.getDepositAmount()));
        context.setVariable("bookingStatus", toStatusLabel(appointment.getStatus()));
        context.setVariable("patientNote", StringUtils.hasText(appointment.getNotes()) ? appointment.getNotes().trim() : "Không có");
        context.setVariable("contactChannel", StringUtils.hasText(appointment.getContactChannel()) ? appointment.getContactChannel() : "Liên hệ tại quầy");
        context.setVariable("contactValue", StringUtils.hasText(appointment.getContactValue()) ? appointment.getContactValue() : "Không có");
        return context;
    }

    private Context buildAppointmentCompletionContext(Appointment appointment, Invoice invoice, BillingNote billingNote) {
        Context context = new Context(VIETNAMESE);
        fillCommonClinicContext(context);

        List<InvoiceEmailItem> invoiceItems = buildInvoiceItems(appointment, billingNote);
        BigDecimal billedTotal = resolveBilledTotal(appointment, invoice, invoiceItems);
        BigDecimal depositAmount = normalizeMoney(appointment.getDepositAmount());
        BigDecimal originalRemaining = resolveOriginalRemaining(invoice, billedTotal, depositAmount);
        BigDecimal discountAmount = invoice != null ? normalizeMoney(invoice.getDiscountAmount()) : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal finalPaymentAmount = invoice != null ? normalizeMoney(invoice.getTotalAmount()) : originalRemaining.subtract(discountAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal paidAmount = depositAmount.add(finalPaymentAmount).setScale(2, RoundingMode.HALF_UP);

        context.setVariable("customerName", resolveCustomerName(appointment));
        context.setVariable("invoiceId", invoice != null ? invoice.getId() : null);
        context.setVariable("appointmentId", appointment.getId());
        context.setVariable("appointmentDate", appointment.getDate() != null ? appointment.getDate().format(DATE_FORMATTER) : "Chưa xác định");
        context.setVariable("appointmentTime", buildTimeRange(appointment));
        context.setVariable("serviceSummary", buildServiceSummary(appointment));
        context.setVariable("dentistName", appointment.getDentist() != null ? appointment.getDentist().getFullName() : "Chưa phân công");
        context.setVariable("invoiceStatusLabel", invoice != null ? resolveInvoiceStatusLabel(invoice.getStatus()) : "Đã thanh toán");
        context.setVariable("invoiceItems", invoiceItems);
        context.setVariable("billedTotal", formatCurrency(billedTotal));
        context.setVariable("depositAmount", formatCurrency(depositAmount));
        context.setVariable("originalRemainingAmount", formatCurrency(originalRemaining));
        context.setVariable("discountAmount", formatCurrency(discountAmount));
        context.setVariable("paidAmount", formatCurrency(paidAmount));
        context.setVariable("voucherCode", invoice != null && invoice.getVoucher() != null ? invoice.getVoucher().getCode() : null);
        context.setVariable("hasDiscount", discountAmount.compareTo(BigDecimal.ZERO) > 0);
        context.setVariable("completionMessage", "Hóa đơn đã được thanh toán xong.");
        return context;
    }

    private void fillCommonClinicContext(Context context) {
        context.setVariable("clinicName", clinicName);
        context.setVariable("clinicAddress", StringUtils.hasText(clinicAddress) ? clinicAddress : "Vui lòng cập nhật trong cấu hình hệ thống");
        context.setVariable("clinicPhone", StringUtils.hasText(clinicPhone) ? clinicPhone : "Vui lòng cập nhật trong cấu hình hệ thống");
        context.setVariable("clinicEmail", StringUtils.hasText(clinicEmail) ? clinicEmail : resolveFromAddress());
    }

    private List<InvoiceEmailItem> buildInvoiceItems(Appointment appointment, BillingNote billingNote) {
        List<InvoiceEmailItem> items = new ArrayList<>();

        if (billingNote != null && billingNote.getPerformedServices() != null && !billingNote.getPerformedServices().isEmpty()) {
            for (BillingPerformedService performedService : billingNote.getPerformedServices()) {
                if (performedService == null || performedService.getService() == null) {
                    continue;
                }
                int qty = Math.max(1, performedService.getQty());
                BigDecimal unitPrice = BigDecimal.valueOf(performedService.getService().getPrice()).setScale(2, RoundingMode.HALF_UP);
                BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(qty)).setScale(2, RoundingMode.HALF_UP);
                items.add(new InvoiceEmailItem(
                        performedService.getService().getName(),
                        qty,
                        formatCurrency(unitPrice),
                        formatCurrency(amount),
                        amount,
                        performedService.getToothNo()
                ));
            }
        }

        if (!items.isEmpty()) {
            return items;
        }

        if (appointment.getAppointmentDetails() != null && !appointment.getAppointmentDetails().isEmpty()) {
            for (AppointmentDetail detail : appointment.getAppointmentDetails()) {
                if (detail == null) {
                    continue;
                }
                BigDecimal unitPrice = normalizeMoney(detail.getPriceSnapshot());
                items.add(new InvoiceEmailItem(
                        StringUtils.hasText(detail.getServiceNameSnapshot()) ? detail.getServiceNameSnapshot().trim() : "Dịch vụ",
                        1,
                        formatCurrency(unitPrice),
                        formatCurrency(unitPrice),
                        unitPrice,
                        null
                ));
            }
        }

        return items;
    }

    private BigDecimal resolveBilledTotal(Appointment appointment, Invoice invoice, List<InvoiceEmailItem> invoiceItems) {
        BigDecimal billedTotal = invoiceItems.stream()
                .map(InvoiceEmailItem::getAmountValue)
                .reduce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), BigDecimal::add);

        if (billedTotal.compareTo(BigDecimal.ZERO) > 0) {
            return billedTotal;
        }
        if (appointment.getTotalAmount() != null) {
            return normalizeMoney(appointment.getTotalAmount());
        }
        if (invoice != null && invoice.getOriginalAmount() != null) {
            return normalizeMoney(invoice.getOriginalAmount()).add(normalizeMoney(appointment.getDepositAmount())).setScale(2, RoundingMode.HALF_UP);
        }
        if (invoice != null && invoice.getTotalAmount() != null) {
            return normalizeMoney(invoice.getTotalAmount())
                    .add(normalizeMoney(appointment.getDepositAmount()))
                    .add(normalizeMoney(invoice.getDiscountAmount()))
                    .setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal resolveOriginalRemaining(Invoice invoice, BigDecimal billedTotal, BigDecimal depositAmount) {
        if (invoice != null && invoice.getOriginalAmount() != null) {
            return normalizeMoney(invoice.getOriginalAmount());
        }
        return billedTotal.subtract(depositAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private void sendHtmlMail(String recipientEmail, String subject, String templateName, Context context) throws Exception {
        String htmlContent = templateEngine.process(templateName, context);

        MimeMessage message = supportMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(
                message,
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                StandardCharsets.UTF_8.name()
        );
        helper.setTo(recipientEmail);
        helper.setSubject(subject);
        helper.setFrom(new InternetAddress(resolveFromAddress(), resolveFromName(), StandardCharsets.UTF_8.name()));
        helper.setText(htmlContent, true);
        supportMailSender.send(message);
    }

    private String resolveRecipientEmail(Appointment appointment) {
        if (appointment.getCustomer() == null || appointment.getCustomer().getUser() == null) {
            return null;
        }
        return appointment.getCustomer().getUser().getEmail();
    }

    private String resolveCustomerName(Appointment appointment) {
        if (appointment.getCustomer() != null && StringUtils.hasText(appointment.getCustomer().getFullName())) {
            return appointment.getCustomer().getFullName().trim();
        }
        return "Quý khách";
    }

    private String buildServiceSummary(Appointment appointment) {
        if (appointment.getAppointmentDetails() != null && !appointment.getAppointmentDetails().isEmpty()) {
            List<String> names = appointment.getAppointmentDetails().stream()
                    .map(AppointmentDetail::getServiceNameSnapshot)
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .distinct()
                    .collect(Collectors.toList());
            if (!names.isEmpty()) {
                return String.join(", ", names);
            }
        }

        if (appointment.getService() != null && StringUtils.hasText(appointment.getService().getName())) {
            return appointment.getService().getName().trim();
        }

        return "Dịch vụ sẽ được cập nhật sau";
    }

    private String buildTimeRange(Appointment appointment) {
        if (appointment.getStartTime() == null || appointment.getEndTime() == null) {
            return "Chưa xác định";
        }
        return appointment.getStartTime().format(TIME_FORMATTER) + " - " + appointment.getEndTime().format(TIME_FORMATTER);
    }

    private String formatCurrency(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getNumberInstance(VIETNAMESE);
        formatter.setMaximumFractionDigits(0);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(normalizeMoney(amount)) + " VND";
    }

    private BigDecimal normalizeMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String resolveInvoiceStatusLabel(PaymentStatus status) {
        if (status == null) {
            return "Không xác định";
        }
        return switch (status) {
            case PAID -> "Đã thanh toán";
            case UNPAID -> "Chưa thanh toán";
        };
    }

    private String toStatusLabel(AppointmentStatus status) {
        if (status == null) {
            return "Chưa xác định";
        }
        return switch (status) {
            case PENDING_DEPOSIT -> "Chờ thanh toán cọc";
            case PENDING -> "Chờ lễ tân xác nhận";
            case CONFIRMED -> "Đã xác nhận";
            case EXAMINING -> "Đang khám";
            case DONE -> "Đã hoàn tất khám";
            case WAITING_PAYMENT -> "Chờ thanh toán";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
            case REEXAM -> "Tái khám";
            default -> status.name();
        };
    }

    private boolean isMailConfigured() {
        return mailEnabled
                && StringUtils.hasText(resolveFromAddress())
                && StringUtils.hasText(smtpUsername)
                && StringUtils.hasText(smtpPassword);
    }

    private boolean isValidEmail(String email) {
        return StringUtils.hasText(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    private String resolveFromAddress() {
        return StringUtils.hasText(fromAddress) ? fromAddress : smtpUsername;
    }

    private String resolveFromName() {
        return StringUtils.hasText(fromName) ? fromName : clinicName;
    }

    private static final class InvoiceEmailItem {
        private final String name;
        private final Integer qty;
        private final String unitPrice;
        private final String amount;
        private final BigDecimal amountValue;
        private final String toothNo;

        private InvoiceEmailItem(String name,
                                 Integer qty,
                                 String unitPrice,
                                 String amount,
                                 BigDecimal amountValue,
                                 String toothNo) {
            this.name = name;
            this.qty = qty;
            this.unitPrice = unitPrice;
            this.amount = amount;
            this.amountValue = amountValue;
            this.toothNo = toothNo;
        }

        public String getName() {
            return name;
        }

        public Integer getQty() {
            return qty;
        }

        public String getUnitPrice() {
            return unitPrice;
        }

        public String getAmount() {
            return amount;
        }

        public BigDecimal getAmountValue() {
            return amountValue;
        }

        public String getToothNo() {
            return toothNo;
        }
    }
}