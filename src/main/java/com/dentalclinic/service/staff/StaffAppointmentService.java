package com.dentalclinic.service.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.model.payment.BillingPerformedService;
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.PaymentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.InvoiceRepository;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import com.dentalclinic.service.dentist.ReexamService;
import com.dentalclinic.service.mail.EmailService;
import com.dentalclinic.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class StaffAppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(StaffAppointmentService.class);

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DentistProfileRepository dentistProfileRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private CustomerAppointmentService customerAppointmentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ReexamService reexamService;

    @Autowired
    private BillingNoteRepository billingNoteRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Transactional
    public void confirmAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn"));

        if (appointment.getDentist() == null) {
            throw new RuntimeException("Phải gán bác sĩ trước khi xác nhận");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Chỉ lịch hẹn ở trạng thái PENDING mới có thể xác nhận");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appointment);

        emailService.sendAppointmentConfirmed(appointment);
    }

    @Transactional
    public void assignDentist(Long appointmentId, Long dentistId) {
        // 1. Tìm lịch hẹn
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn"));

        Long oldDentistId = appt.getDentist() != null ? appt.getDentist().getId() : null;

        // 2. Kiểm tra trùng lịch của bác sĩ mới
        boolean hasOverlap = appointmentRepository.hasOverlappingAppointment(
                dentistId,
                appt.getDate(),
                appt.getStartTime(),
                appt.getEndTime()
        );

        if (hasOverlap) {
            throw new RuntimeException("Bác sĩ đã có lịch trong khung giờ này");
        }

        // 3. Tìm thông tin bác sĩ
        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy bác sĩ"));

        // 4. Cập nhật bác sĩ phụ trách
        appt.setDentist(dentist);

        // 5. TỰ ĐỘNG CHUYỂN TR� NG THÁI: Nếu Ä‘ang PENDING thì chuyển sang CONFIRMED
        if (appt.getStatus() == AppointmentStatus.PENDING) {
            appt.setStatus(AppointmentStatus.CONFIRMED);
        }

        // 6. Lưu thay đổi
        Appointment saved = appointmentRepository.save(appt);

        // 7. Gửi Email xác nhận cho khách h� ng (Tận dụng h� m confirm đã có của bạn)
        try {
            emailService.sendAppointmentConfirmed(saved);
        } catch (Exception e) {
            // Log lỗi gửi mail nhưng không l� m rollback giao dịch gán bác sĩ
            System.err.println("Lỗi gửi email xác nhận: " + e.getMessage());
        }

        // 8. Thông báo hệ thống
        if (oldDentistId == null || !oldDentistId.equals(dentistId)) {
            notificationService.notifyBookingUpdated(saved, "Đã gán bác sĩ và xác nhận lịch hẹn thành công");
        }
    }

    @Transactional
    public void completeAppointment(Long id) {
        throw new RuntimeException("Không còn hỗ trợ chuyển trực tiếp sang COMPLETED. Luồng đúng là EXAMINING -> DONE -> WAITING_PAYMENT -> COMPLETED.");
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, String reason) {
        customerAppointmentService.cancelAppointmentByStaff(appointmentId, reason);
    }

    public Page<Appointment> searchAndSort(
            String keyword,
            String serviceKeyword,
            String sort,
            int page
    ) {
        Sort s = Sort.by("date").ascending();

        if ("newest".equals(sort)) {
            s = Sort.by("date").descending();
        } else if ("oldest".equals(sort)) {
            s = Sort.by("date").ascending();
        }

        Pageable pageable = PageRequest.of(page, 3, s);

        boolean hasCustomer = keyword != null && !keyword.trim().isEmpty();
        boolean hasService = serviceKeyword != null && !serviceKeyword.trim().isEmpty();

        if (hasCustomer && hasService) {
            return appointmentRepository
                    .findByCustomer_FullNameContainingIgnoreCaseAndService_NameContainingIgnoreCase(
                            keyword, serviceKeyword, pageable);
        }

        if (hasCustomer) {
            return appointmentRepository
                    .findByCustomer_FullNameContainingIgnoreCase(keyword, pageable);
        }

        if (hasService) {
            return appointmentRepository
                    .findByService_NameContainingIgnoreCase(serviceKeyword, pageable);
        }

        return appointmentRepository.findAll(pageable);
    }
    public void checkInAppointment(Long id) {
        throw new RuntimeException("Không còn hỗ trợ bước check-in. Sau CONFIRMED, bác sĩ sẽ chuyển lịch sang EXAMINING khi bắt đầu khám.");
    }

    public List<DentistProfile> getAvailableDentistsForAppointment(Long appointmentId) {
        // 1. Tìm thông tin cuộc hẹn để biết ng� y khách đặt
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn"));

        // 2. Lấy bác sĩ ACTIVE và không nghỉ được duyệt trong ngày hẹn
        return dentistProfileRepository.findAvailableDentistsForDate(appt.getDate());
    }

    @Transactional
    public void processPayment(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn"));

        if (a.getStatus() != AppointmentStatus.DONE) {
            throw new RuntimeException("Chỉ lịch hẹn có trạng thái DONE mới có thể tiến hành thanh toán.");
        }

        BillingNote billingNote = billingNoteRepository.findByAppointment_Id(id)
                .orElseThrow(() -> new RuntimeException("Chưa có phiếu điều trị cho lịch hẹn này."));

        BigDecimal billingTotal = calculateBillingTotal(a, billingNote);
        BigDecimal depositAmount = a.getDepositAmount() != null
                ? a.getDepositAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = billingTotal.subtract(depositAmount).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        Invoice invoice = invoiceRepository.findByAppointment_Id(id).orElseGet(Invoice::new);
        invoice.setAppointment(a);
        invoice.setTotalAmount(remainingAmount);

        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus(PaymentStatus.PAID);
            invoiceRepository.save(invoice);
            a.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(a);
            notificationService.notifyBookingUpdated(a, "Đã hoàn tất thanh toán");
            return;
        }

        invoice.setStatus(PaymentStatus.UNPAID);
        invoiceRepository.save(invoice);
        a.setStatus(AppointmentStatus.WAITING_PAYMENT);
        appointmentRepository.save(a);
        notificationService.notifyBookingUpdated(a, "Đã tạo hóa đơn thanh toán còn lại");
    }

    private BigDecimal calculateBillingTotal(Appointment appointment, BillingNote billingNote) {
        BigDecimal total = BigDecimal.ZERO;

        if (billingNote.getPerformedServices() != null && !billingNote.getPerformedServices().isEmpty()) {
            for (BillingPerformedService item : billingNote.getPerformedServices()) {
                if (item.getService() == null) {
                    continue;
                }
                BigDecimal price = BigDecimal.valueOf(item.getService().getPrice()).setScale(2, RoundingMode.HALF_UP);
                int qty = Math.max(1, item.getQty());
                total = total.add(price.multiply(BigDecimal.valueOf(qty)));
            }
        } else if (appointment.getTotalAmount() != null) {
            total = appointment.getTotalAmount().setScale(2, RoundingMode.HALF_UP);
        }

        return total.setScale(2, RoundingMode.HALF_UP);
    }

}

