package com.dentalclinic.controller.customer;

import com.dentalclinic.config.VNPayConfig;
import com.dentalclinic.dto.customer.FinalPaymentPreviewDto;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.model.payment.PaymentStatus;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.InvoiceRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import com.dentalclinic.service.notification.NotificationService;
import com.dentalclinic.service.wallet.WalletService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@Controller
@RequestMapping("/customer/payment")
public class CustomerPaymentController {

    private static final String SESSION_USER_ID = "userId";

    @Autowired
    private VNPayConfig vnPayConfig;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CustomerAppointmentService customerAppointmentService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private NotificationService notificationService;

    @GetMapping("/create-deposit/{id}")
    public String createDepositPayment(@PathVariable Long id, HttpServletRequest request, HttpSession session) throws Exception {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        Appointment appointment = appointmentRepository.findByIdAndCustomer_User_Id(id, userId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen ID: " + id));

        if (appointment.getStatus() != AppointmentStatus.PENDING_DEPOSIT) {
            return "redirect:/customer/book?status=fail&id=" + id;
        }

        BigDecimal depositAmount = appointment.getDepositAmount();
        if (depositAmount == null && appointment.getTotalAmount() != null) {
            depositAmount = appointment.getTotalAmount().multiply(BigDecimal.valueOf(0.5d));
        }
        if (depositAmount == null && appointment.getService() != null) {
            depositAmount = BigDecimal.valueOf(appointment.getService().getPrice()).multiply(BigDecimal.valueOf(0.5d));
        }
        if (depositAmount == null || depositAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("So tien dat coc khong hop le cho lich hen ID: " + id);
        }

        long amount = depositAmount.multiply(BigDecimal.valueOf(100L)).longValue();
        String txnRef = String.valueOf(System.currentTimeMillis());
        Map<String, String> vnpParams = buildVnpayParams(amount, txnRef, "ThanhToanLichHen" + id, request);
        String paymentUrl = buildPaymentUrl(vnpParams);
        return "redirect:" + paymentUrl;
    }

    @PostMapping("/deposit/{id}/wallet")
    @ResponseBody
    public ResponseEntity<?> payDepositWithWallet(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            Appointment appointment = customerAppointmentService.payDepositWithWallet(userId, id);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "appointmentId", appointment.getId(),
                    "status", appointment.getStatus().name()
            ));
        } catch (RuntimeException ex) {
            String message = ex.getMessage() != null ? ex.getMessage() : "Khong the thanh toan bang vi.";
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", message
            ));
        }
    }

    @PostMapping("/wallet/topup/create")
    @ResponseBody
    public ResponseEntity<?> createWalletTopup(@RequestParam BigDecimal amount, HttpServletRequest request, HttpSession session) throws Exception {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        BigDecimal normalizedAmount = amount == null ? BigDecimal.ZERO : amount.stripTrailingZeros();
        if (normalizedAmount.compareTo(BigDecimal.valueOf(10000L)) < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "So tien nap toi thieu la 10.000 VND."
            ));
        }

        long vnpAmount = normalizedAmount.multiply(BigDecimal.valueOf(100L)).longValue();
        String txnRef = "WALLET" + System.currentTimeMillis();
        String orderInfo = "WALLET_TOPUP_" + userId + "_" + txnRef;

        Map<String, String> vnpParams = buildVnpayParams(vnpAmount, txnRef, orderInfo, request);
        String paymentUrl = buildPaymentUrl(vnpParams);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "paymentUrl", paymentUrl
        ));
    }

    @GetMapping("/create-final-payment/{id}")
    public String createFinalPayment(@PathVariable Long id,
                                     @RequestParam(required = false) String voucherCode,
                                     HttpServletRequest request,
                                     HttpSession session) throws Exception {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        try {
            FinalPaymentPreviewDto preview = customerAppointmentService.applyVoucherForFinalPayment(userId, id, voucherCode);
            if (preview.getPayableAmount() == null || preview.getPayableAmount().compareTo(BigDecimal.ZERO) <= 0) {
                customerAppointmentService.completeFinalPayment(id, preview.getInvoiceId());
                return "redirect:/customer/my-appointments?payment=success#highlight=" + id;
            }

            long amount = preview.getPayableAmount().multiply(BigDecimal.valueOf(100L)).longValue();
            String txnRef = "FINAL" + System.currentTimeMillis();
            String orderInfo = "FINAL_PAYMENT_" + id + "_" + preview.getInvoiceId() + "_" + txnRef;
            String paymentUrl = buildPaymentUrl(buildVnpayParams(amount, txnRef, orderInfo, request));
            return "redirect:" + paymentUrl;
        } catch (RuntimeException ex) {
            return "redirect:/customer/my-appointments?payment=fail#highlight=" + id;
        }
    }

    @GetMapping("/final-payment/{id}/preview")
    @ResponseBody
    public ResponseEntity<?> previewFinalPayment(@PathVariable Long id,
                                                 @RequestParam(required = false) String voucherCode,
                                                 HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            FinalPaymentPreviewDto preview = customerAppointmentService.previewFinalPayment(userId, id, voucherCode);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "data", preview
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage() != null ? ex.getMessage() : "Không thể áp dụng voucher."
            ));
        }
    }

    @PostMapping("/final-payment/{id}/wallet")
    @ResponseBody
    public ResponseEntity<?> payFinalPaymentWithWallet(@PathVariable Long id,
                                                       @RequestParam(required = false) String voucherCode,
                                                       HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            Appointment appointment = customerAppointmentService.payRemainingWithWallet(userId, id, voucherCode);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "appointmentId", appointment.getId(),
                    "status", appointment.getStatus().name()
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage() != null ? ex.getMessage() : "Khong the thanh toan bang vi."
            ));
        }
    }

    @GetMapping("/vnpay-return")
    public String paymentReturn(HttpServletRequest request) {
        try {
            Map<String, String> fields = new HashMap<>();
            for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements(); ) {
                String name = params.nextElement();
                String value = request.getParameter(name);
                if (value != null && !value.isEmpty()) {
                    fields.put(name, value);
                }
            }

            String vnp_SecureHash = fields.remove("vnp_SecureHash");
            List<String> fieldNames = new ArrayList<>(fields.keySet());
            Collections.sort(fieldNames);

            StringBuilder hashData = new StringBuilder();
            for (String fieldName : fieldNames) {
                String fieldValue = fields.get(fieldName);
                if (fieldValue != null && !fieldValue.isEmpty()) {
                    hashData.append(fieldName).append('=').append(fieldValue).append('&');
                }
            }
            if (hashData.length() > 0) {
                hashData.setLength(hashData.length() - 1);
            }

            String checkHash = vnPayConfig.hmacSHA512(vnPayConfig.hashSecret, hashData.toString());
            Long appointmentId = extractAppointmentId(request.getParameter("vnp_OrderInfo"));
            String responseCode = request.getParameter("vnp_ResponseCode");
            String orderInfo = request.getParameter("vnp_OrderInfo");

            if (vnp_SecureHash == null || !checkHash.equalsIgnoreCase(vnp_SecureHash)) {
                return isWalletTopupOrder(orderInfo)
                        ? "redirect:/customer/wallet?topup=fail" + buildWalletTxnRefQuery(orderInfo)
                        : (isFinalPaymentOrder(orderInfo)
                        ? "redirect:/customer/my-appointments?payment=fail"
                        : "redirect:/customer/book?status=fail");
            }

            if (isWalletTopupOrder(orderInfo)) {
                return handleWalletTopupReturn(request, responseCode, orderInfo);
            }

            if (isFinalPaymentOrder(orderInfo)) {
                return handleFinalPaymentReturn(responseCode, orderInfo);
            }

            if ("00".equals(responseCode) && appointmentId != null) {
                customerAppointmentService.markDepositPaymentSuccess(appointmentId);
                return "redirect:/customer/book?status=success&id=" + appointmentId;
            }

            if (appointmentId != null) {
                customerAppointmentService.cancelUnpaidAppointment(
                        appointmentId,
                        "Khach hang da huy hoac khong hoan tat thanh toan VNPay."
                );
                return "redirect:/customer/book?status=fail&id=" + appointmentId;
            }
            return "redirect:/customer/book?status=fail";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/customer/book?status=error";
        }
    }

    @PostMapping("/appointments/cancel-back/{id}")
    @ResponseBody
    public ResponseEntity<?> cancelOnBack(@PathVariable Long id) {
        customerAppointmentService.cancelUnpaidAppointment(id, "Khach hang quay lai tu trang thanh toan");
        return ResponseEntity.ok().build();
    }

    private Long extractAppointmentId(String orderInfo) {
        if (orderInfo == null || orderInfo.isBlank()) {
            return null;
        }
        String numeric = orderInfo.replaceAll("\\D+", "");
        if (numeric.isBlank()) {
            return null;
        }
        return Long.parseLong(numeric);
    }

    private boolean isWalletTopupOrder(String orderInfo) {
        return orderInfo != null && orderInfo.startsWith("WALLET_TOPUP_");
    }

    private boolean isFinalPaymentOrder(String orderInfo) {
        return orderInfo != null && orderInfo.startsWith("FINAL_PAYMENT_");
    }

    private String handleWalletTopupReturn(HttpServletRequest request, String responseCode, String orderInfo) {
        String[] parts = orderInfo.split("_", 4);
        if (parts.length < 4) {
            return "redirect:/customer/wallet?topup=fail";
        }

        Long userId = Long.parseLong(parts[2]);
        String txnRef = parts[3];

        if (!"00".equals(responseCode)) {
            return "redirect:/customer/wallet?topup=fail&txnRef=" + txnRef;
        }

        CustomerProfile customer = customerProfileRepository.findByUser_Id(userId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay khach hang de nap vi."));

        BigDecimal amount = new BigDecimal(request.getParameter("vnp_Amount"))
                .divide(BigDecimal.valueOf(100L));
        String description = "Nap tien vi qua VNPay [" + txnRef + "]";

        if (!walletTransactionRepository.existsByTypeAndDescription(WalletTransactionType.DEPOSIT, description)) {
            walletService.deposit(customer, amount, description, null);
        }

        return "redirect:/customer/wallet?topup=success&txnRef=" + txnRef;
    }

    private String buildWalletTxnRefQuery(String orderInfo) {
        if (!isWalletTopupOrder(orderInfo)) {
            return "";
        }

        String[] parts = orderInfo.split("_", 4);
        if (parts.length < 4 || parts[3] == null || parts[3].isBlank()) {
            return "";
        }

        return "&txnRef=" + parts[3];
    }

    private String handleFinalPaymentReturn(String responseCode, String orderInfo) {
        String[] parts = orderInfo.split("_", 5);
        if (parts.length < 5) {
            return "redirect:/customer/my-appointments?payment=fail";
        }

        Long appointmentId = Long.parseLong(parts[2]);
        Long invoiceId = Long.parseLong(parts[3]);

        if (!"00".equals(responseCode)) {
            return "redirect:/customer/my-appointments?payment=fail#highlight=" + appointmentId;
        }

        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen thanh toan."));
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Khong tim thay hoa don thanh toan."));

        if (invoice.getAppointment() == null || !appointmentId.equals(invoice.getAppointment().getId())) {
            return "redirect:/customer/my-appointments?payment=fail#highlight=" + appointmentId;
        }

        customerAppointmentService.completeFinalPayment(appointmentId, invoiceId);

        return "redirect:/customer/my-appointments?payment=success#highlight=" + appointmentId;
    }

    private Map<String, String> buildVnpayParams(long amount, String txnRef, String orderInfo, HttpServletRequest request) {
        Map<String, String> vnpParams = new HashMap<>();
        vnpParams.put("vnp_Version", "2.1.0");
        vnpParams.put("vnp_Command", "pay");
        vnpParams.put("vnp_TmnCode", vnPayConfig.tmnCode);
        vnpParams.put("vnp_Amount", String.valueOf(amount));
        vnpParams.put("vnp_CurrCode", "VND");
        vnpParams.put("vnp_TxnRef", txnRef);
        vnpParams.put("vnp_OrderInfo", orderInfo);
        vnpParams.put("vnp_OrderType", "other");
        vnpParams.put("vnp_Locale", "vn");
        vnpParams.put("vnp_ReturnUrl", vnPayConfig.returnUrl);
        vnpParams.put("vnp_IpAddr", request.getRemoteAddr() != null ? request.getRemoteAddr() : "127.0.0.1");

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnpParams.put("vnp_CreateDate", formatter.format(cld.getTime()));

        cld.add(Calendar.MINUTE, 15);
        vnpParams.put("vnp_ExpireDate", formatter.format(cld.getTime()));
        return vnpParams;
    }

    private String buildPaymentUrl(Map<String, String> vnpParams) throws Exception {
        List<String> fieldNames = new ArrayList<>(vnpParams.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnpParams.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                query.append(URLEncoder.encode(fieldName, StandardCharsets.UTF_8.toString()));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString()));

                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.UTF_8.toString()));

                if (fieldNames.indexOf(fieldName) != fieldNames.size() - 1) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String vnpSecureHash = vnPayConfig.hmacSHA512(vnPayConfig.hashSecret, hashData.toString());
        return vnPayConfig.payUrl + "?" + query + "&vnp_SecureHash=" + vnpSecureHash;
    }

    private Long getCurrentUserId(HttpSession session) {
        Object uid = session.getAttribute(SESSION_USER_ID);
        Long userId = null;
        if (uid instanceof Long) {
            userId = (Long) uid;
        } else if (uid instanceof Number) {
            userId = ((Number) uid).longValue();
        }
        if (userId == null || !userRepository.existsById(userId)) {
            return null;
        }
        return userId;
    }
}
