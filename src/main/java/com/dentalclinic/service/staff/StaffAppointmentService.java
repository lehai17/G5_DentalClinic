package com.dentalclinic.service.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.model.payment.BillingPerformedService;
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.PaymentStatus;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.InvoiceRepository;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class StaffAppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(StaffAppointmentService.class);

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DentistProfileRepository dentistProfileRepository;

    @Autowired
    private DentistBusyScheduleRepository dentistBusyScheduleRepository;

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
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        if (appointment.getDentist() == null) {
            throw new RuntimeException("Phai gan bac si truoc khi xac nhan");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Chi lich hen o trang thai PENDING moi co the xac nhan");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appointment);

        emailService.sendAppointmentConfirmed(appointment);

        // Dentist inbox notification
        notificationService.notifyDentistAppointmentConfirmed(appointment);
    }

    @Transactional
    public void assignDentist(Long appointmentId, Long dentistId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        Long oldDentistId = appt.getDentist() != null ? appt.getDentist().getId() : null;

        boolean hasOverlap = appointmentRepository.hasOverlappingAppointmentExcludingSelf(
                dentistId,
                appt.getDate(),
                appt.getStartTime(),
                appt.getEndTime(),
                appt.getId()
        );

        if (hasOverlap) {
            throw new RuntimeException("Bac si da co lich trong khung gio nay");
        }

        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay bac si"));

        appt.setDentist(dentist);

        if (appt.getStatus() == AppointmentStatus.PENDING) {
            appt.setStatus(AppointmentStatus.CONFIRMED);
        }

        Appointment saved = appointmentRepository.save(appt);

        // Dentist inbox notification (confirmed/assigned appointment)
        notificationService.notifyDentistAppointmentConfirmed(saved);

        try {
            emailService.sendAppointmentConfirmed(saved);
        } catch (Exception e) {
            logger.warn("Khong gui duoc email xac nhan cho appointment {}", saved.getId(), e);
        }

        if (oldDentistId == null || !oldDentistId.equals(dentistId)) {
            notificationService.notifyBookingUpdated(saved, "Da doi nha si phu trach lich hen");
        }
    }

    @Transactional
    public void completeAppointment(Long id) {
        throw new RuntimeException("Khong con ho tro chuyen truc tiep sang COMPLETED. Luong dung la EXAMINING -> DONE -> WAITING_PAYMENT -> COMPLETED.");
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, String reason) {
        customerAppointmentService.cancelAppointmentByStaff(appointmentId, reason);
    }

    public Page<Appointment> searchAndSort(String keyword,
                                           String serviceKeyword,
                                           String sort,
                                           int page) {
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
            return appointmentRepository.findByCustomer_FullNameContainingIgnoreCase(keyword, pageable);
        }

        if (hasService) {
            return appointmentRepository.findByService_NameContainingIgnoreCase(serviceKeyword, pageable);
        }

        return appointmentRepository.findAll(pageable);
    }

    public void checkInAppointment(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuntimeException("Only CONFIRMED appointment can be checked-in");
        }

        a.setStatus(AppointmentStatus.CHECKED_IN);
        appointmentRepository.save(a);

        // Dentist inbox notification
        notificationService.notifyDentistAppointmentCheckedIn(a);
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuntimeException("Chi lich hen o trang thai CONFIRMED moi co the check-in");
        }

        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        Appointment saved = appointmentRepository.save(appointment);
        notificationService.notifyBookingUpdated(saved, "Da check-in tai quay le tan");
    }

    public List<DentistProfile> getAvailableDentistsForAppointment(Long appointmentId) {
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        List<DentistProfile> candidates = dentistProfileRepository.findAvailableDentistsForDate(appt.getDate());
        List<DentistProfile> availableDentists = new ArrayList<>();

        for (DentistProfile dentist : candidates) {
            if (dentist == null || dentist.getId() == null) {
                continue;
            }
            if (appt.getDentist() != null && dentist.getId().equals(appt.getDentist().getId())) {
                continue;
            }
            boolean hasOverlap = appointmentRepository.hasOverlappingAppointmentExcludingSelf(
                    dentist.getId(),
                    appt.getDate(),
                    appt.getStartTime(),
                    appt.getEndTime(),
                    appt.getId()
            );
            if (!hasOverlap) {
                availableDentists.add(dentist);
            }
        }

        return availableDentists;
    }

    public Map<Long, Boolean> buildDentistLeaveFlags(List<Appointment> appointments) {
        Map<Long, Boolean> leaveFlags = new HashMap<>();
        if (appointments == null) {
            return leaveFlags;
        }

        for (Appointment appointment : appointments) {
            boolean dentistOnLeave = appointment != null
                    && appointment.getDentist() != null
                    && appointment.getDentist().getId() != null
                    && appointment.getDate() != null
                    && dentistBusyScheduleRepository.existsApprovedLeaveByDentistAndDate(
                            appointment.getDentist().getId(),
                            appointment.getDate()
                    );
            leaveFlags.put(appointment.getId(), dentistOnLeave);
        }
        return leaveFlags;
    }

    @Transactional
    public void processPayment(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        if (a.getStatus() != AppointmentStatus.DONE) {
            throw new RuntimeException("Chi lich hen co trang thai DONE moi co the tien hanh thanh toan.");
        }

        BillingNote billingNote = billingNoteRepository.findByAppointment_Id(id)
                .orElseThrow(() -> new RuntimeException("Chua co phieu dieu tri cho lich hen nay."));

        BigDecimal billingTotal = calculateBillingTotal(a, billingNote);
        BigDecimal depositAmount = a.getDepositAmount() != null
                ? a.getDepositAmount().setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        BigDecimal remainingAmount = billingTotal.subtract(depositAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        Invoice invoice = invoiceRepository.findByAppointment_Id(id).orElseGet(Invoice::new);
        invoice.setAppointment(a);
        invoice.setTotalAmount(remainingAmount);

        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus(PaymentStatus.PAID);
            invoiceRepository.save(invoice);
            a.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(a);
            notificationService.notifyBookingUpdated(a, "Da hoan tat thanh toan");
            return;
        }

        invoice.setStatus(PaymentStatus.UNPAID);
        invoiceRepository.save(invoice);
        a.setStatus(AppointmentStatus.WAITING_PAYMENT);
        appointmentRepository.save(a);
        notificationService.notifyBookingUpdated(a, "Da tao hoa don thanh toan con lai");
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
