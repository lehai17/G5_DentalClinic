package com.dentalclinic.service.customer;

import com.dentalclinic.dto.customer.CustomerPaymentHistoryItemDto;
import com.dentalclinic.dto.customer.CustomerPaymentReceiptDto;
import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentDetail;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.PaymentStatus;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.InvoiceRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CustomerPaymentHistoryService {

    private static final String DEPOSIT_RECORD = "DEPOSIT";
    private static final String INVOICE_RECORD = "INVOICE";
    private static final String REFUND_RECORD = "REFUND";
    private static final String DEPOSIT_DESCRIPTION_KEY = "thanh toan tien coc lich hen #";
    private static final String FINAL_PAYMENT_DESCRIPTION_KEY = "thanh toan hoa don con lai lich hen #";

    private final AppointmentRepository appointmentRepository;
    private final InvoiceRepository invoiceRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public CustomerPaymentHistoryService(AppointmentRepository appointmentRepository,
                                         InvoiceRepository invoiceRepository,
                                         WalletTransactionRepository walletTransactionRepository,
                                         CustomerProfileRepository customerProfileRepository) {
        this.appointmentRepository = appointmentRepository;
        this.invoiceRepository = invoiceRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.customerProfileRepository = customerProfileRepository;
    }

    @Transactional(readOnly = true)
    public List<CustomerPaymentHistoryItemDto> getPaymentHistory(Long userId) {
        CustomerProfile customer = getCustomer(userId);
        List<Appointment> appointments = appointmentRepository.findByCustomer_User_IdOrderByDateDesc(userId);
        List<Invoice> invoices = invoiceRepository.findByAppointment_Customer_User_IdOrderByCreatedAtDesc(userId);
        List<WalletTransaction> appointmentTransactions =
                walletTransactionRepository.findByWallet_Customer_User_IdAndAppointmentIdIsNotNullOrderByCreatedAtDesc(userId);

        Map<Long, List<WalletTransaction>> transactionsByAppointment = appointmentTransactions.stream()
                .filter(tx -> tx.getAppointmentId() != null)
                .collect(Collectors.groupingBy(WalletTransaction::getAppointmentId));

        List<CustomerPaymentHistoryItemDto> items = new ArrayList<>();

        for (Appointment appointment : appointments) {
            if (!hasDepositReceipt(appointment)) {
                continue;
            }
            WalletTransaction depositTransaction = findDepositTransaction(transactionsByAppointment.get(appointment.getId()));
            items.add(buildDepositHistoryItem(customer, appointment, depositTransaction));
        }

        for (Invoice invoice : invoices) {
            Appointment appointment = invoice.getAppointment();
            WalletTransaction finalPaymentTransaction = appointment == null
                    ? null
                    : findFinalPaymentTransaction(transactionsByAppointment.get(appointment.getId()));
            items.add(buildInvoiceHistoryItem(customer, invoice, finalPaymentTransaction));
        }

        for (WalletTransaction transaction : appointmentTransactions) {
            if (transaction.getType() != WalletTransactionType.REFUND) {
                continue;
            }
            items.add(buildRefundHistoryItem(customer, transaction, findAppointment(appointments, transaction.getAppointmentId())));
        }

        items.sort(Comparator
                .comparing(CustomerPaymentHistoryItemDto::getRecordedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(CustomerPaymentHistoryItemDto::getAppointmentDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(CustomerPaymentHistoryItemDto::getAppointmentStartTime, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(CustomerPaymentHistoryItemDto::getAppointmentId, Comparator.nullsLast(Comparator.reverseOrder())));
        return items;
    }

    @Transactional(readOnly = true)
    public CustomerPaymentReceiptDto getDepositReceipt(Long userId, Long appointmentId) {
        CustomerProfile customer = getCustomer(userId);
        Appointment appointment = appointmentRepository.findByIdAndCustomer_User_Id(appointmentId, userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy lịch hẹn phù hợp."));

        if (!hasDepositReceipt(appointment)) {
            throw new BusinessException("Lịch hẹn này chưa có biên nhận đặt cọc.");
        }

        WalletTransaction transaction = findDepositTransaction(
                walletTransactionRepository.findByAppointmentId(appointmentId)
        );

        CustomerPaymentReceiptDto receipt = buildBaseReceipt(customer, appointment);
        receipt.setRecordType(DEPOSIT_RECORD);
        receipt.setRecordTypeLabel("Đặt cọc");
        receipt.setTitle("Biên nhận đặt cọc");
        receipt.setAmount(scaleMoney(appointment.getDepositAmount()));
        receipt.setPaymentStatusLabel("Đã thanh toán");
        receipt.setWalletTransactionId(transaction == null ? null : transaction.getId());
        receipt.setPaymentMethodLabel(resolveDepositMethod(transaction));
        receipt.setReferenceCode(transaction != null
                ? "WALLET-TX-" + transaction.getId()
                : "APPOINTMENT-" + appointment.getId());
        receipt.setRecordedAt(transaction != null ? transaction.getCreatedAt() : appointment.getCreatedAt());
        receipt.setNote(transaction != null
                ? safeText(transaction.getDescription(), "Thanh toán đặt cọc cho lịch hẹn.")
                : "Hệ thống đã ghi nhận khoản đặt cọc cho lịch hẹn này.");
        receipt.setBackUrl("/customer/payments");
        return receipt;
    }

    @Transactional(readOnly = true)
    public CustomerPaymentReceiptDto getInvoiceReceipt(Long userId, Long invoiceId) {
        CustomerProfile customer = getCustomer(userId);
        Invoice invoice = invoiceRepository.findByIdAndAppointment_Customer_User_Id(invoiceId, userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hóa đơn phù hợp."));

        Appointment appointment = invoice.getAppointment();
        if (appointment == null) {
            throw new BusinessException("Hóa đơn này không còn liên kết lịch hẹn hợp lệ.");
        }

        WalletTransaction transaction = findFinalPaymentTransaction(
                walletTransactionRepository.findByAppointmentId(appointment.getId())
        );

        CustomerPaymentReceiptDto receipt = buildBaseReceipt(customer, appointment);
        receipt.setRecordType(INVOICE_RECORD);
        receipt.setRecordTypeLabel("Thanh toán hóa đơn");
        receipt.setTitle("Chi tiết thanh toán");
        receipt.setInvoiceId(invoice.getId());
        receipt.setAmount(scaleMoney(invoice.getTotalAmount()));
        receipt.setPaymentStatusLabel(resolveInvoiceStatusLabel(invoice.getStatus()));
        receipt.setWalletTransactionId(transaction == null ? null : transaction.getId());
        receipt.setPaymentMethodLabel(resolveInvoiceMethod(invoice, transaction));
        receipt.setReferenceCode(transaction != null
                ? "WALLET-TX-" + transaction.getId()
                : "INVOICE-" + invoice.getId());
        receipt.setRecordedAt(transaction != null ? transaction.getCreatedAt() : invoice.getCreatedAt());
        receipt.setNote(transaction != null
                ? safeText(transaction.getDescription(), "Thanh toán phần còn lại cho lịch hẹn.")
                : buildInvoiceNote(invoice));
        receipt.setBackUrl("/customer/payments");
        return receipt;
    }

    @Transactional(readOnly = true)
    public CustomerPaymentReceiptDto getWalletReceipt(Long userId, Long walletTransactionId) {
        CustomerProfile customer = getCustomer(userId);
        WalletTransaction transaction = walletTransactionRepository.findByIdAndWallet_Customer_User_Id(walletTransactionId, userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy giao dịch ví phù hợp."));

        Appointment appointment = null;
        if (transaction.getAppointmentId() != null) {
            appointment = appointmentRepository.findByIdAndCustomer_User_Id(transaction.getAppointmentId(), userId)
                    .orElse(null);
        }

        CustomerPaymentReceiptDto receipt = appointment != null
                ? buildBaseReceipt(customer, appointment)
                : new CustomerPaymentReceiptDto();

        receipt.setRecordType(REFUND_RECORD);
        receipt.setRecordTypeLabel("Hoàn tiền");
        receipt.setTitle("Biên nhận giao dịch ví");
        receipt.setCustomerName(customer.getFullName());
        receipt.setWalletTransactionId(transaction.getId());
        receipt.setAmount(scaleMoney(transaction.getAmount()));
        receipt.setPaymentMethodLabel("Ví của tôi");
        receipt.setPaymentStatusLabel(resolveWalletStatusLabel(transaction));
        receipt.setReferenceCode("WALLET-TX-" + transaction.getId());
        receipt.setRecordedAt(transaction.getCreatedAt());
        receipt.setNote(safeText(transaction.getDescription(), "Giao dịch ví liên quan đến lịch hẹn."));
        receipt.setBackUrl("/customer/payments");
        return receipt;
    }

    private CustomerPaymentHistoryItemDto buildDepositHistoryItem(CustomerProfile customer,
                                                                 Appointment appointment,
                                                                 WalletTransaction transaction) {
        CustomerPaymentHistoryItemDto item = buildBaseHistoryItem(customer, appointment);
        item.setRecordType(DEPOSIT_RECORD);
        item.setRecordTypeLabel("Đặt cọc");
        item.setAmount(scaleMoney(appointment.getDepositAmount()));
        item.setPaymentMethodLabel(resolveDepositMethod(transaction));
        item.setPaymentStatusLabel("Đã thanh toán");
        item.setReferenceCode(transaction != null
                ? "WALLET-TX-" + transaction.getId()
                : "APPOINTMENT-" + appointment.getId());
        item.setRecordedAt(transaction != null ? transaction.getCreatedAt() : appointment.getCreatedAt());
        item.setWalletTransactionId(transaction == null ? null : transaction.getId());
        item.setReceiptUrl("/customer/payments/deposit/" + appointment.getId());
        item.setShortDescription("Khoản đặt cọc cho lịch hẹn #" + appointment.getId());
        return item;
    }

    private CustomerPaymentHistoryItemDto buildInvoiceHistoryItem(CustomerProfile customer,
                                                                 Invoice invoice,
                                                                 WalletTransaction transaction) {
        Appointment appointment = invoice.getAppointment();
        CustomerPaymentHistoryItemDto item = buildBaseHistoryItem(customer, appointment);
        item.setRecordType(INVOICE_RECORD);
        item.setRecordTypeLabel("Thanh toán hóa đơn");
        item.setInvoiceId(invoice.getId());
        item.setAmount(scaleMoney(invoice.getTotalAmount()));
        item.setPaymentMethodLabel(resolveInvoiceMethod(invoice, transaction));
        item.setPaymentStatusLabel(resolveInvoiceStatusLabel(invoice.getStatus()));
        item.setReferenceCode(transaction != null
                ? "WALLET-TX-" + transaction.getId()
                : "INVOICE-" + invoice.getId());
        item.setRecordedAt(transaction != null ? transaction.getCreatedAt() : invoice.getCreatedAt());
        item.setWalletTransactionId(transaction == null ? null : transaction.getId());
        item.setReceiptUrl("/customer/payments/invoice/" + invoice.getId());
        item.setShortDescription("Hóa đơn thanh toán cho lịch hẹn #" + (appointment != null ? appointment.getId() : ""));
        return item;
    }

    private CustomerPaymentHistoryItemDto buildRefundHistoryItem(CustomerProfile customer,
                                                                WalletTransaction transaction,
                                                                Appointment appointment) {
        CustomerPaymentHistoryItemDto item = buildBaseHistoryItem(customer, appointment);
        item.setRecordType(REFUND_RECORD);
        item.setRecordTypeLabel("Hoàn tiền");
        item.setWalletTransactionId(transaction.getId());
        item.setAmount(scaleMoney(transaction.getAmount()));
        item.setPaymentMethodLabel("Ví của tôi");
        item.setPaymentStatusLabel(resolveWalletStatusLabel(transaction));
        item.setReferenceCode("WALLET-TX-" + transaction.getId());
        item.setRecordedAt(transaction.getCreatedAt());
        item.setReceiptUrl("/customer/payments/wallet/" + transaction.getId());
        item.setShortDescription(safeText(transaction.getDescription(), "Khoản hoàn tiền liên quan đến lịch hẹn."));
        return item;
    }

    private CustomerPaymentHistoryItemDto buildBaseHistoryItem(CustomerProfile customer, Appointment appointment) {
        CustomerPaymentHistoryItemDto item = new CustomerPaymentHistoryItemDto();
        if (appointment != null) {
            item.setAppointmentId(appointment.getId());
            item.setAppointmentDate(appointment.getDate());
            item.setAppointmentStartTime(appointment.getStartTime());
            item.setServiceSummary(resolveServiceSummary(appointment));
            item.setDentistName(appointment.getDentist() != null ? appointment.getDentist().getFullName() : "Chưa phân công");
        }
        return item;
    }

    private CustomerPaymentReceiptDto buildBaseReceipt(CustomerProfile customer, Appointment appointment) {
        CustomerPaymentReceiptDto receipt = new CustomerPaymentReceiptDto();
        receipt.setCustomerName(customer.getFullName());
        receipt.setAppointmentId(appointment.getId());
        receipt.setAppointmentDate(appointment.getDate());
        receipt.setAppointmentStartTime(appointment.getStartTime());
        receipt.setAppointmentEndTime(appointment.getEndTime());
        receipt.setServiceSummary(resolveServiceSummary(appointment));
        receipt.setServiceNames(resolveServiceNames(appointment));
        receipt.setDentistName(appointment.getDentist() != null ? appointment.getDentist().getFullName() : "Chưa phân công");
        return receipt;
    }

    private CustomerProfile getCustomer(Long userId) {
        return customerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new BusinessException("Không tìm thấy hồ sơ khách hàng hiện tại."));
    }

    private boolean hasDepositReceipt(Appointment appointment) {
        return appointment != null
                && appointment.getDepositAmount() != null
                && appointment.getDepositAmount().compareTo(BigDecimal.ZERO) > 0
                && appointment.getStatus() != AppointmentStatus.PENDING_DEPOSIT;
    }

    private WalletTransaction findDepositTransaction(List<WalletTransaction> transactions) {
        return findPaymentTransactionByKeyword(transactions, DEPOSIT_DESCRIPTION_KEY);
    }

    private WalletTransaction findFinalPaymentTransaction(List<WalletTransaction> transactions) {
        return findPaymentTransactionByKeyword(transactions, FINAL_PAYMENT_DESCRIPTION_KEY);
    }

    private WalletTransaction findPaymentTransactionByKeyword(List<WalletTransaction> transactions, String keyword) {
        if (transactions == null) {
            return null;
        }
        String safeKeyword = keyword.toLowerCase(Locale.ROOT);
        return transactions.stream()
                .filter(Objects::nonNull)
                .filter(tx -> tx.getType() == WalletTransactionType.PAYMENT)
                .filter(tx -> {
                    String description = tx.getDescription();
                    return description != null && description.toLowerCase(Locale.ROOT).contains(safeKeyword);
                })
                .max(Comparator.comparing(WalletTransaction::getCreatedAt, Comparator.nullsLast(LocalDateTime::compareTo)))
                .orElse(null);
    }

    private Appointment findAppointment(List<Appointment> appointments, Long appointmentId) {
        if (appointmentId == null || appointments == null) {
            return null;
        }
        return appointments.stream()
                .filter(appointment -> Objects.equals(appointment.getId(), appointmentId))
                .findFirst()
                .orElse(null);
    }

    private String resolveDepositMethod(WalletTransaction transaction) {
        return transaction != null ? "Ví của tôi" : "VNPay";
    }

    private String resolveInvoiceMethod(Invoice invoice, WalletTransaction transaction) {
        if (transaction != null) {
            return "Ví của tôi";
        }
        if (invoice.getStatus() == PaymentStatus.PAID) {
            return "VNPay";
        }
        return "Chưa thanh toán";
    }

    private String resolveInvoiceStatusLabel(PaymentStatus status) {
        return status == PaymentStatus.PAID ? "Đã thanh toán" : "Chưa thanh toán";
    }

    private String resolveWalletStatusLabel(WalletTransaction transaction) {
        if (transaction == null || transaction.getStatus() == null) {
            return "Không xác định";
        }
        return switch (transaction.getStatus()) {
            case COMPLETED -> "Hoàn tất";
            case PENDING -> "Đang xử lý";
            case FAILED -> "Thất bại";
            case CANCELLED -> "Đã hủy";
        };
    }

    private String resolveServiceSummary(Appointment appointment) {
        List<String> names = resolveServiceNames(appointment);
        if (names.isEmpty()) {
            return "Chưa có dịch vụ";
        }
        if (names.size() == 1) {
            return names.get(0);
        }
        return String.join(", ", names);
    }

    private List<String> resolveServiceNames(Appointment appointment) {
        if (appointment == null) {
            return List.of();
        }
        if (appointment.getAppointmentDetails() != null && !appointment.getAppointmentDetails().isEmpty()) {
            return appointment.getAppointmentDetails().stream()
                    .map(AppointmentDetail::getServiceNameSnapshot)
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(name -> !name.isBlank())
                    .distinct()
                    .toList();
        }
        if (appointment.getService() != null && appointment.getService().getName() != null) {
            return List.of(appointment.getService().getName());
        }
        return List.of();
    }

    private BigDecimal scaleMoney(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private String buildInvoiceNote(Invoice invoice) {
        if (invoice.getStatus() == PaymentStatus.PAID) {
            return "Hệ thống đã ghi nhận thanh toán cho hóa đơn này.";
        }
        return "Hóa đơn này đã được tạo nhưng chưa hoàn tất thanh toán.";
    }
}
