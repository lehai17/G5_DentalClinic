package com.dentalclinic.controller.customer;

import com.dentalclinic.config.VNPayConfig;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Controller
@RequestMapping("/customer/payment")
public class CustomerPaymentController {

    @Autowired
    private VNPayConfig vnPayConfig;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @GetMapping("/create-deposit/{id}")
    public String createDepositPayment(@PathVariable Long id, HttpServletRequest request) throws Exception {

        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy lịch hẹn ID: " + id));

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

        // --- BẮT ĐẦU PHẦN QUAN TRỌNG NHẤT ---
        List<String> fieldNames = new ArrayList<>(vnp_Params.keySet());
        Collections.sort(fieldNames); // Sắp xếp Alphabet

        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        for (String fieldName : fieldNames) {
            String fieldValue = vnp_Params.get(fieldName);
            if ((fieldValue != null) && (fieldValue.length() > 0)) {

                // 1. Build HashData: key=value
                hashData.append(fieldName);
                hashData.append('=');
                // Lưu ý: VNPay yêu cầu giá trị để băm PHẢI là chuỗi đã được URLEncode (chuẩn RFC3986)
                // Đây là điểm gây tranh cãi nhất, nhưng version 2.1.0 yêu cầu encode cả trong hashData
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                // 2. Build Query: key=value
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII.toString()));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII.toString()));

                if (fieldNames.indexOf(fieldName) != fieldNames.size() - 1) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        // Tạo chữ ký từ chuỗi đã sắp xếp và đã encode
        String vnp_SecureHash = vnPayConfig.hmacSHA512(vnPayConfig.hashSecret, hashData.toString());

        // Final link
        String paymentUrl = vnPayConfig.payUrl + "?" + query.toString() + "&vnp_SecureHash=" + vnp_SecureHash;

        System.out.println("DEBUG HASH DATA: " + hashData.toString());
        return "redirect:" + paymentUrl;
    }

    @GetMapping("/vnpay-return")
    public String paymentReturn(HttpServletRequest request) {
        try {
            Map<String, String> fields = new HashMap<>();
            for (Enumeration<String> params = request.getParameterNames(); params.hasMoreElements();) {
                String name = params.nextElement();
                String value = request.getParameter(name);
                if (value != null && value.length() > 0) {
                    fields.put(name, value);
                }
            }

            String vnp_SecureHash = fields.remove("vnp_SecureHash");
            List<String> fieldNames = new ArrayList<>(fields.keySet());
            Collections.sort(fieldNames);

            StringBuilder hashData = new StringBuilder();
            for (String fieldName : fieldNames) {
                String fieldValue = fields.get(fieldName);
                if ((fieldValue != null) && (fieldValue.length() > 0)) {
                    hashData.append(fieldName).append('=').append(fieldValue).append('&');
                }
            }
            if (hashData.length() > 0) hashData.setLength(hashData.length() - 1);

            String checkHash = vnPayConfig.hmacSHA512(vnPayConfig.hashSecret, hashData.toString());

            // So sánh chữ ký không phân biệt hoa thường
            if (checkHash.equalsIgnoreCase(vnp_SecureHash)) {
                if ("00".equals(request.getParameter("vnp_ResponseCode"))) {

                    // LẤY ID AN TOÀN: Chỉ lấy các chữ số từ vnp_OrderInfo (Ví dụ: "ThanhToan205" -> 205)
                    String orderInfo = request.getParameter("vnp_OrderInfo");
                    Long appointmentId = Long.parseLong(orderInfo.replaceAll("\\D+", ""));

                    // Cập nhật Database
                    Appointment appointment = appointmentRepository.findById(appointmentId).orElseThrow();
                    appointment.setStatus(AppointmentStatus.PENDING);
                    appointmentRepository.save(appointment);

                    // CHUYỂN HƯỚNG VỀ STEP 4: Gửi status=success
                    return "redirect:/customer/book?status=success&id=" + appointmentId;
                }
            }

            // Nếu thất bại hoặc sai chữ ký
            return "redirect:/customer/book?status=fail";

        } catch (Exception e) {
            // In lỗi ra console để debug nếu vẫn bị lỗi 500
            e.printStackTrace();
            return "redirect:/customer/book?status=error";
        }
    }
}