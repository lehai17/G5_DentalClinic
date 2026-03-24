package com.dentalclinic.service.staff;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.AppointmentInvoiceItemDto;
import com.dentalclinic.dto.customer.AppointmentPrescriptionItemDto;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.payment.BillingNote;
import com.dentalclinic.model.payment.BillingPerformedService;
import com.dentalclinic.model.payment.BillingPrescriptionItem;
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.PaymentStatus;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.BillingNoteRepository;
import com.dentalclinic.repository.DentistBusyScheduleRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.InvoiceRepository;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import com.dentalclinic.service.dentist.ReexamService;
import com.dentalclinic.service.mail.EmailService;
import com.dentalclinic.service.notification.NotificationService;
import com.dentalclinic.service.wallet.WalletService;
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
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class StaffAppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(StaffAppointmentService.class);
    private static final BigDecimal FINAL_DEPOSIT_RATE = new BigDecimal("0.50");

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

    @Autowired
    private WalletService walletService;

    @Autowired
    private PayOsService payOsService;

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

        if (oldDentistId == null || !oldDentistId.equals(dentistId)) {
            notificationService.notifyBookingUpdated(saved, "Da doi nha si phu trach lich hen");
        }
    }

    @Transactional
    public void completeAppointment(Long id) {
        throw new RuntimeException("Khong con ho tro chuyen truc tiep sang COMPLETED. Luong dung la EXAMINING -> WAITING_PAYMENT -> COMPLETED.");
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

        return appointmentRepository.findAllBy(pageable);
    }

    public Map<Long, String> buildServiceSummaries(List<Appointment> appointments) {
        Map<Long, String> summaries = new HashMap<>();
        if (appointments == null) {
            return summaries;
        }

        for (Appointment appointment : appointments) {
            if (appointment == null || appointment.getId() == null) {
                continue;
            }
            summaries.put(appointment.getId(), buildServiceSummary(appointment));
        }
        return summaries;
    }

    public void checkInAppointment(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuntimeException("Only CONFIRMED appointment can be checked-in");
        }

        appointment.setStatus(AppointmentStatus.CHECKED_IN);
        Appointment saved = appointmentRepository.save(appointment);

        // Dentist inbox notification
        notificationService.notifyDentistAppointmentCheckedIn(saved);
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
            if (appointment == null || appointment.getId() == null) {
                continue;
            }
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

        if (a.getStatus() != AppointmentStatus.WAITING_PAYMENT) {
            throw new RuntimeException("Chi lich hen co trang thai WAITING_PAYMENT moi co the tien hanh thanh toan.");
        }

        BillingNote billingNote = billingNoteRepository.findByAppointment_Id(id)
                .orElseThrow(() -> new RuntimeException("Chua co phieu dieu tri cho lich hen nay."));

        BigDecimal billingTotal = calculateBillingTotal(a, billingNote);
        BigDecimal depositAmount = calculateFinalDepositAmount(billingTotal);
        a.setDepositAmount(depositAmount);
        BigDecimal remainingAmount = billingTotal.subtract(depositAmount)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);

        Invoice invoice = invoiceRepository.findByAppointment_Id(id).orElseGet(Invoice::new);
        invoice.setAppointment(a);
        invoice.setVoucher(null);
        invoice.setVoucherUsageCounted(false);
        invoice.setOriginalAmount(remainingAmount);
        invoice.setDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(remainingAmount);
        invoice.setPayOsOrderCode(null);
        invoice.setPayOsPaymentLinkId(null);
        invoice.setPayOsCheckoutUrl(null);
        invoice.setPayOsQrCode(null);
        invoice.setPayOsStatus(null);
        invoice.setPayOsReference(null);
        invoice.setPayOsPaidAt(null);

        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus(PaymentStatus.PAID);
            invoiceRepository.save(invoice);
            a.setStatus(AppointmentStatus.COMPLETED);
            appointmentRepository.save(a);
            notificationService.notifyBookingUpdated(a, "Da hoan tat thanh toan");
            emailService.sendAppointmentCompletionIfNeeded(a.getId());
            return;
        }

        invoice.setStatus(PaymentStatus.UNPAID);
        invoiceRepository.save(invoice);
        a.setStatus(AppointmentStatus.WAITING_PAYMENT);
        appointmentRepository.save(a);
        notificationService.notifyBookingUpdated(a, "Da tao hoa don thanh toan con lai");
    }

    @Transactional(readOnly = true)
    public AppointmentDto getInvoicePreview(Long id) {
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        if (appointment.getStatus() != AppointmentStatus.WAITING_PAYMENT
                && appointment.getStatus() != AppointmentStatus.COMPLETED) {
            throw new RuntimeException("Chi co the xem hoa don cho lich hen da kham xong.");
        }

        BillingNote billingNote = billingNoteRepository.findByAppointment_Id(id)
                .orElseThrow(() -> new RuntimeException("Chua co phieu dieu tri cho lich hen nay."));

        AppointmentDto dto = new AppointmentDto();
        dto.setId(appointment.getId());
        dto.setStatus(appointment.getStatus().name());
        dto.setDate(appointment.getDate());
        dto.setStartTime(appointment.getStartTime());
        dto.setEndTime(appointment.getEndTime());
        dto.setBillingNoteId(billingNote.getId());
        dto.setBillingNoteNote(billingNote.getNote());
        dto.setBillingNoteUpdatedAt(billingNote.getUpdatedAt());

        if (appointment.getDentist() != null) {
            dto.setDentistId(appointment.getDentist().getId());
            dto.setDentistName(appointment.getDentist().getFullName());
        }

        List<AppointmentInvoiceItemDto> invoiceItems = new ArrayList<>();
        List<AppointmentPrescriptionItemDto> prescriptionItems = new ArrayList<>();
        BigDecimal billedTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        List<String> serviceNames = new ArrayList<>();

        if (billingNote.getPerformedServices() != null) {
            for (BillingPerformedService item : billingNote.getPerformedServices()) {
                if (item == null || item.getService() == null) {
                    continue;
                }

                int qty = Math.max(1, item.getQty());
                BigDecimal unitPrice = BigDecimal.valueOf(item.getService().getPrice())
                        .setScale(2, RoundingMode.HALF_UP);
                BigDecimal lineAmount = unitPrice.multiply(BigDecimal.valueOf(qty))
                        .setScale(2, RoundingMode.HALF_UP);

                AppointmentInvoiceItemDto line = new AppointmentInvoiceItemDto();
                line.setId(item.getId());
                line.setServiceId(item.getService().getId());
                line.setName(item.getService().getName());
                line.setQty(qty);
                line.setUnitPrice(unitPrice);
                line.setAmount(lineAmount);
                invoiceItems.add(line);

                if (item.getService().getName() != null && !item.getService().getName().isBlank()) {
                    serviceNames.add(item.getService().getName().trim());
                }

                billedTotal = billedTotal.add(lineAmount);
            }
        }

        if (billingNote.getPrescriptionItems() != null) {
            for (BillingPrescriptionItem item : billingNote.getPrescriptionItems()) {
                if (item == null) {
                    continue;
                }
                AppointmentPrescriptionItemDto line = new AppointmentPrescriptionItemDto();
                line.setId(item.getId());
                line.setMedicineName(item.getMedicineName());
                line.setDosage(item.getDosage());
                line.setNote(item.getNote());
                prescriptionItems.add(line);
            }
        }

        dto.setInvoiceItems(invoiceItems);
        dto.setPrescriptionItems(prescriptionItems);
        dto.setBilledTotal(billedTotal);
        dto.setDepositAmount(calculateFinalDepositAmount(billedTotal));
        dto.setServiceName(serviceNames.stream().distinct().collect(Collectors.joining(", ")));

        BigDecimal originalRemainingAmount = billedTotal.subtract(dto.getDepositAmount())
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP);
        dto.setOriginalRemainingAmount(originalRemainingAmount);
        dto.setDiscountAmount(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        dto.setRemainingAmount(originalRemainingAmount);
        dto.setInvoiceStatus(PaymentStatus.UNPAID.name());

        invoiceRepository.findByAppointment_Id(id).ifPresent(invoice -> {
            dto.setInvoiceId(invoice.getId());
            dto.setInvoiceStatus(invoice.getStatus() != null ? invoice.getStatus().name() : PaymentStatus.UNPAID.name());
            dto.setOriginalRemainingAmount(invoice.getOriginalAmount() != null
                    ? invoice.getOriginalAmount().setScale(2, RoundingMode.HALF_UP)
                    : originalRemainingAmount);
            dto.setDiscountAmount(invoice.getDiscountAmount() != null
                    ? invoice.getDiscountAmount().setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            dto.setRemainingAmount(invoice.getTotalAmount() != null
                    ? invoice.getTotalAmount().max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                    : originalRemainingAmount);
        });

        dto.setCanPayRemaining(appointment.getStatus() == AppointmentStatus.WAITING_PAYMENT);
        return dto;
    }

    private BigDecimal calculateFinalDepositAmount(BigDecimal billingTotal) {
        BigDecimal normalizedTotal = billingTotal == null
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : billingTotal.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        return normalizedTotal.multiply(FINAL_DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional
    public Map<String, Object> preparePaymentOptions(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        if (appointment.getStatus() == AppointmentStatus.WAITING_PAYMENT) {
            processPayment(appointmentId);
            appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));
        }

        AppointmentDto invoicePreview = getInvoicePreview(appointmentId);
        CustomerProfile customer = appointment.getCustomer();
        BigDecimal walletBalance = customer != null
                ? walletService.getBalance(customer).setScale(2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

        String transferContent = invoicePreview.getInvoiceId() != null
                ? "G5HD" + invoicePreview.getInvoiceId()
                : "G5LH" + appointmentId;

        Map<String, Object> payload = new HashMap<>();
        payload.put("invoice", invoicePreview);
        payload.put("walletBalance", walletBalance);
        payload.put("transferContent", transferContent);
        return payload;
    }

    @Transactional
    public Map<String, Object> createPayOsQr(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        if (appointment.getStatus() == AppointmentStatus.WAITING_PAYMENT) {
            processPayment(appointmentId);
        }

        Invoice invoice = invoiceRepository.findByAppointment_Id(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay hoa don thanh toan."));

        PayOsService.PayOsLinkData linkData = payOsService.createOrReusePaymentLink(invoice);
        Map<String, Object> payload = new HashMap<>();
        payload.put("paymentLinkId", linkData.paymentLinkId());
        payload.put("orderCode", linkData.orderCode());
        payload.put("checkoutUrl", linkData.checkoutUrl());
        payload.put("qrImageUrl", linkData.qrImageUrl());
        payload.put("status", linkData.status());
        return payload;
    }

    @Transactional
    public Map<String, Object> getPayOsPaymentStatus(Long appointmentId) {
        Invoice invoice = invoiceRepository.findByAppointment_Id(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay hoa don thanh toan."));

        PayOsService.PayOsStatusData statusData = payOsService.syncStatus(invoice);
        Map<String, Object> payload = new HashMap<>();
        payload.put("invoiceStatus", statusData.invoiceStatus());
        payload.put("appointmentStatus", statusData.appointmentStatus());
        payload.put("payOsStatus", statusData.payOsStatus());
        payload.put("paymentLinkId", statusData.paymentLinkId());
        payload.put("orderCode", statusData.orderCode());
        payload.put("paid", "PAID".equalsIgnoreCase(statusData.invoiceStatus())
                || "COMPLETED".equalsIgnoreCase(statusData.appointmentStatus()));
        return payload;
    }

    @Transactional
    public Appointment payWithWalletByStaff(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        if (appointment.getCustomer() == null
                || appointment.getCustomer().getUser() == null
                || appointment.getCustomer().getUser().getId() == null) {
            throw new RuntimeException("Khong tim thay tai khoan khach hang de tru vi.");
        }

        if (appointment.getStatus() == AppointmentStatus.WAITING_PAYMENT) {
            processPayment(appointmentId);
        }

        return customerAppointmentService.payRemainingWithWallet(
                appointment.getCustomer().getUser().getId(),
                appointmentId,
                null);
    }

    @Transactional
    public Appointment confirmManualPayment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));

        if (appointment.getStatus() == AppointmentStatus.WAITING_PAYMENT) {
            processPayment(appointmentId);
            appointment = appointmentRepository.findById(appointmentId)
                    .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen"));
        }

        Invoice invoice = invoiceRepository.findByAppointment_Id(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay hoa don thanh toan."));

        return customerAppointmentService.completeFinalPayment(appointmentId, invoice.getId());
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

    private String buildServiceSummary(Appointment appointment) {
        if (appointment.getAppointmentDetails() != null && !appointment.getAppointmentDetails().isEmpty()) {
            List<String> names = appointment.getAppointmentDetails().stream()
                    .map(detail -> detail != null ? detail.getServiceNameSnapshot() : null)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isEmpty())
                    .distinct()
                    .collect(Collectors.toList());
            if (!names.isEmpty()) {
                return String.join(", ", names);
            }
        }

        if (appointment.getService() != null && appointment.getService().getName() != null) {
            String serviceName = appointment.getService().getName().trim();
            if (!serviceName.isEmpty()) {
                return serviceName;
            }
        }

        return "Chua co dich vu";
    }

}
