package com.dentalclinic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class VNPayConfig {

    @Value("${vnpay.tmnCode}")
    public String tmnCode;

    @Value("${vnpay.hashSecret}")
    public String hashSecret;

    @Value("${vnpay.payUrl}")
    public String payUrl;

    @Value("${vnpay.returnUrl}")
    public String returnUrl;

    // Trong VNPayConfig.java
    public String hmacSHA512(String key, String data) throws Exception {
        Mac hmac512 = Mac.getInstance("HmacSHA512");
        SecretKeySpec secretKey = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
        hmac512.init(secretKey);
        byte[] bytes = hmac512.doFinal(data.getBytes(StandardCharsets.UTF_8));

        StringBuilder hash = new StringBuilder();
        for (byte b : bytes) {
            hash.append(String.format("%02x", b));
        }
        return hash.toString(); // Thử chữ thường trước, nếu không được mới dùng .toUpperCase()
    }
}