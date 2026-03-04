package com.dentalclinic.controller.customer;

import com.dentalclinic.config.VNPayConfig;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

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

    @Autowired
    private VNPayConfig vnPayConfig;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private CustomerAppointmentService customerAppointmentService;

    @GetMapping("/create-deposit/{id}")
    public String createDepositPayment(@PathVariable Long id, HttpServletRequest request) throws Exception {

        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Khong tim thay lich hen ID: " + id));

        long amount = (long) (appointment.getService().getPrice() * 0.5 * 100);
        String txnRef = String.valueOf(System.currentTimeMillis());

        Map<String, String> vnp_Params = new HashMap<>();
        vnp_Params.put("vnp_Version", "2.1.0");
        vnp_Params.put("vnp_Command", "pay");
        vnp_Params.put("vnp_TmnCode", vnPayConfig.tmnCode);
        vnp_Params.put("vnp_Amount", String.valueOf(amount));
        vnp_Params.put("vnp_CurrCode", "VND");
        vnp_Params.put("vnp_TxnRef", txnRef);
        vnp_Params.put("vnp_OrderInfo", "ThanhToanLichHen" + id);
        vnp_Params.put("vnp_OrderType", "other");
        vnp_Params.put("vnp_Locale", "vn");
        vnp_Params.put("vnp_ReturnUrl", vnPayConfig.returnUrl);
        vnp_Params.put("vnp_IpAddr", "127.0.0.1");

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        vnp_Params.put("vnp_CreateDate", formatter.format(cld.getTime()));

        cld.add(Calendar.MINUTE, 15);
        vnp_Params.put("vnp_ExpireDate", formatter.format(cld.getTime()));

        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames);

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if (fieldValue != null && !fieldValue.isEmpty()) {
                hashData.append(fieldName)
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()))
                        .append('=')
                        .append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                if (fieldNames.indexOf(fieldName) != fieldNames.size() - 1) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String vnp_SecureHash = vnPayConfig.hmacSHA512(vnPayConfig.hashSecret, hashData.toString());
        String paymentUrl = vnPayConfig.payUrl + "?" + query + "&vnp_SecureHash=" + vnp_SecureHash;

        return "redirect:" + paymentUrl;
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

            if (checkHash.equalsIgnoreCase(vnp_SecureHash) && "00".equals(responseCode) && appointmentId != null) {
                customerAppointmentService.markDepositPaymentSuccess(appointmentId);
                return "redirect:/customer/book?status=success&id=" + appointmentId;
            }

            if (appointmentId != null) {
                customerAppointmentService.cancelUnpaidAppointment(
                        appointmentId,
                        "Huy tu dong vi khong hoan tat thanh toan dat coc."
                );
            }
            return "redirect:/customer/book?status=fail";

        } catch (Exception e) {
            e.printStackTrace();
            return "redirect:/customer/book?status=error";
        }
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
}
