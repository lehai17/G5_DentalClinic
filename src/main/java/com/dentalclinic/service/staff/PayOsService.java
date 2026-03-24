package com.dentalclinic.service.staff;

import com.dentalclinic.config.PayOsProperties;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.payment.Invoice;
import com.dentalclinic.repository.InvoiceRepository;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class PayOsService {

    private final PayOsProperties payOsProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final InvoiceRepository invoiceRepository;
    private final CustomerAppointmentService customerAppointmentService;

    public PayOsService(PayOsProperties payOsProperties,
                        RestTemplate restTemplate,
                        InvoiceRepository invoiceRepository,
                        CustomerAppointmentService customerAppointmentService) {
        this.payOsProperties = payOsProperties;
        this.restTemplate = restTemplate;
        this.invoiceRepository = invoiceRepository;
        this.customerAppointmentService = customerAppointmentService;
        this.objectMapper = new ObjectMapper();
    }

    @Transactional
    public PayOsLinkData createOrReusePaymentLink(Invoice invoice) {
        validateConfiguration();

        if (invoice == null || invoice.getAppointment() == null) {
            throw new RuntimeException("Khong tim thay hoa don de tao lien ket payOS.");
        }

        Appointment appointment = invoice.getAppointment();
        if (appointment.getStatus() != AppointmentStatus.WAITING_PAYMENT) {
            throw new RuntimeException("Chi tao lien ket payOS cho lich hen dang cho thanh toan.");
        }

        if (StringUtils.hasText(invoice.getPayOsCheckoutUrl())
                && StringUtils.hasText(invoice.getPayOsQrCode())
                && !"PAID".equalsIgnoreCase(invoice.getPayOsStatus())) {
            return toLinkData(invoice);
        }

        long orderCode = generateOrderCode(invoice);
        long amount = normalizeAmount(invoice.getTotalAmount());
        String description = buildDescription(invoice);
        long expiredAt = Instant.now().plusSeconds(15 * 60).getEpochSecond();

        Map<String, Object> payload = new TreeMap<>();
        payload.put("orderCode", orderCode);
        payload.put("amount", amount);
        payload.put("description", description);
        payload.put("cancelUrl", payOsProperties.getCancelUrl());
        payload.put("returnUrl", payOsProperties.getReturnUrl());
        payload.put("expiredAt", expiredAt);
        payload.put("items", List.of(Map.of(
                "name", buildItemName(invoice),
                "quantity", 1,
                "price", amount
        )));
        payload.put("signature", createCreatePaymentSignature(orderCode, amount, description));

        HttpHeaders headers = createApiHeaders();
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                payOsProperties.getBaseUrl() + "/v2/payment-requests",
                entity,
                String.class
        );

        JsonNode root = readTree(response.getBody());
        JsonNode data = root.path("data");
        if (!response.getStatusCode().is2xxSuccessful() || data.isMissingNode() || data.isNull()) {
            throw new RuntimeException(resolveApiMessage(root, "Khong tao duoc lien ket payOS."));
        }

        invoice.setPayOsOrderCode(data.path("orderCode").asLong(orderCode));
        invoice.setPayOsPaymentLinkId(firstText(data, "paymentLinkId", "id"));
        invoice.setPayOsCheckoutUrl(readText(data, "checkoutUrl"));
        invoice.setPayOsQrCode(readText(data, "qrCode"));
        invoice.setPayOsStatus(readText(data, "status"));
        invoiceRepository.save(invoice);
        return toLinkData(invoice);
    }

    @Transactional
    public PayOsStatusData syncStatus(Invoice invoice) {
        if (invoice == null) {
            throw new RuntimeException("Khong tim thay hoa don.");
        }

        if (!StringUtils.hasText(invoice.getPayOsPaymentLinkId()) && invoice.getPayOsOrderCode() == null) {
            return toStatusData(invoice);
        }

        validateConfiguration();
        String lookupId = StringUtils.hasText(invoice.getPayOsPaymentLinkId())
                ? invoice.getPayOsPaymentLinkId()
                : String.valueOf(invoice.getPayOsOrderCode());

        HttpEntity<Void> entity = new HttpEntity<>(createApiHeaders());
        ResponseEntity<String> response = restTemplate.exchange(
                payOsProperties.getBaseUrl() + "/v2/payment-requests/" + lookupId,
                HttpMethod.GET,
                entity,
                String.class
        );

        JsonNode root = readTree(response.getBody());
        JsonNode data = root.path("data");
        if (response.getStatusCode().is2xxSuccessful() && !data.isMissingNode() && !data.isNull()) {
            invoice.setPayOsStatus(readText(data, "status"));
            if (StringUtils.hasText(firstText(data, "paymentLinkId", "id"))) {
                invoice.setPayOsPaymentLinkId(firstText(data, "paymentLinkId", "id"));
            }
            if ("PAID".equalsIgnoreCase(invoice.getPayOsStatus())) {
                invoiceRepository.save(invoice);
                customerAppointmentService.completeFinalPayment(invoice.getAppointment().getId(), invoice.getId());
                invoice = invoiceRepository.findById(invoice.getId()).orElse(invoice);
            } else {
                invoiceRepository.save(invoice);
            }
        }

        return toStatusData(invoice);
    }

    @Transactional
    public void handleWebhook(String rawPayload) {
        JsonNode root = readTree(rawPayload);
        JsonNode dataNode = root.path("data");
        String signature = readText(root, "signature");

        if (dataNode.isMissingNode() || dataNode.isNull() || !StringUtils.hasText(signature)) {
            throw new RuntimeException("Webhook payOS khong hop le.");
        }

        if (!verifyWebhookData(dataNode, signature)) {
            throw new RuntimeException("Chu ky webhook payOS khong hop le.");
        }

        Invoice invoice = null;
        if (!dataNode.path("orderCode").isMissingNode() && !dataNode.path("orderCode").isNull()) {
            invoice = invoiceRepository.findByPayOsOrderCode(dataNode.path("orderCode").asLong()).orElse(null);
        }
        if (invoice == null && StringUtils.hasText(firstText(dataNode, "paymentLinkId", "id"))) {
            invoice = invoiceRepository.findByPayOsPaymentLinkId(firstText(dataNode, "paymentLinkId", "id")).orElse(null);
        }
        if (invoice == null) {
            return;
        }

        invoice.setPayOsStatus(firstText(dataNode, "status", "code"));
        if (StringUtils.hasText(firstText(dataNode, "paymentLinkId", "id"))) {
            invoice.setPayOsPaymentLinkId(firstText(dataNode, "paymentLinkId", "id"));
        }
        if (StringUtils.hasText(firstText(dataNode, "reference", "referenceCode"))) {
            invoice.setPayOsReference(firstText(dataNode, "reference", "referenceCode"));
        }
        if (StringUtils.hasText(firstText(dataNode, "transactionDateTime", "paidAt"))) {
            invoice.setPayOsPaidAt(parsePayOsDateTime(firstText(dataNode, "transactionDateTime", "paidAt")));
        }
        invoiceRepository.save(invoice);

        if ("00".equals(readText(dataNode, "code")) || "PAID".equalsIgnoreCase(readText(dataNode, "status"))) {
            customerAppointmentService.completeFinalPayment(invoice.getAppointment().getId(), invoice.getId());
        }
    }

    public String createQrImageUrl(String qrCode) {
        if (!StringUtils.hasText(qrCode)) {
            return null;
        }
        return "https://api.qrserver.com/v1/create-qr-code/?size=280x280&data="
                + URLEncoder.encode(qrCode, StandardCharsets.UTF_8);
    }

    private HttpHeaders createApiHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-client-id", payOsProperties.getClientId());
        headers.set("x-api-key", payOsProperties.getApiKey());
        return headers;
    }

    private void validateConfiguration() {
        if (!StringUtils.hasText(payOsProperties.getClientId())
                || !StringUtils.hasText(payOsProperties.getApiKey())
                || !StringUtils.hasText(payOsProperties.getChecksumKey())) {
            throw new RuntimeException("Chua cau hinh payOS trong application.properties hoac bien moi truong.");
        }
    }

    private long generateOrderCode(Invoice invoice) {
        if (invoice.getPayOsOrderCode() != null && invoice.getPayOsOrderCode() > 0) {
            return invoice.getPayOsOrderCode();
        }
        long seed = System.currentTimeMillis() % 1_000_000_000L;
        long invoicePart = invoice.getId() == null ? 0L : invoice.getId() % 10_000L;
        return seed * 10_000L + invoicePart;
    }

    private long normalizeAmount(BigDecimal amount) {
        BigDecimal normalized = amount == null
                ? BigDecimal.ZERO
                : amount.max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP);
        return normalized.longValue();
    }

    private String buildDescription(Invoice invoice) {
        Long invoiceId = invoice.getId();
        String description = "G5HD" + (invoiceId == null ? "0000" : invoiceId);
        return description.length() > 25 ? description.substring(0, 25) : description;
    }

    private String buildItemName(Invoice invoice) {
        Long appointmentId = invoice.getAppointment() != null ? invoice.getAppointment().getId() : null;
        return appointmentId == null ? "Hoa don phong kham" : "Hoa don lich hen #" + appointmentId;
    }

    private String createCreatePaymentSignature(long orderCode, long amount, String description) {
        String data = "amount=" + amount
                + "&cancelUrl=" + payOsProperties.getCancelUrl()
                + "&description=" + description
                + "&orderCode=" + orderCode
                + "&returnUrl=" + payOsProperties.getReturnUrl();
        return hmacSha256(data, payOsProperties.getChecksumKey());
    }

    private boolean verifyWebhookData(JsonNode dataNode, String signature) {
        TreeMap<String, String> sorted = new TreeMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = dataNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            sorted.put(entry.getKey(), normalizeWebhookValue(entry.getValue()));
        }

        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : sorted.entrySet()) {
            parts.add(entry.getKey() + "=" + entry.getValue());
        }
        String payload = String.join("&", parts);
        return hmacSha256(payload, payOsProperties.getChecksumKey()).equalsIgnoreCase(signature);
    }

    private String normalizeWebhookValue(JsonNode value) {
        if (value == null || value.isNull()) {
            return "";
        }
        if (value.isArray() || value.isObject()) {
            return value.toString();
        }
        return value.asText("");
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new RuntimeException("Khong the tao chu ky payOS.", ex);
        }
    }

    private JsonNode readTree(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception ex) {
            throw new RuntimeException("Du lieu payOS khong hop le.", ex);
        }
    }

    private String readText(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isMissingNode() || child.isNull() ? null : child.asText();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = readText(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String resolveApiMessage(JsonNode root, String fallback) {
        String message = firstText(root, "desc", "message");
        return StringUtils.hasText(message) ? message : fallback;
    }

    private LocalDateTime parsePayOsDateTime(String value) {
        try {
            return LocalDateTime.parse(value.replace(" ", "T"));
        } catch (Exception ignored) {
            try {
                return LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault());
            } catch (Exception ex) {
                return null;
            }
        }
    }

    private PayOsLinkData toLinkData(Invoice invoice) {
        return new PayOsLinkData(
                invoice.getPayOsOrderCode(),
                invoice.getPayOsPaymentLinkId(),
                invoice.getPayOsCheckoutUrl(),
                createQrImageUrl(invoice.getPayOsQrCode()),
                invoice.getPayOsStatus()
        );
    }

    private PayOsStatusData toStatusData(Invoice invoice) {
        Appointment appointment = invoice.getAppointment();
        return new PayOsStatusData(
                invoice.getStatus() != null ? invoice.getStatus().name() : null,
                appointment != null && appointment.getStatus() != null ? appointment.getStatus().name() : null,
                invoice.getPayOsStatus(),
                invoice.getPayOsPaymentLinkId(),
                invoice.getPayOsOrderCode()
        );
    }

    public record PayOsLinkData(Long orderCode,
                                String paymentLinkId,
                                String checkoutUrl,
                                String qrImageUrl,
                                String status) {
    }

    public record PayOsStatusData(String invoiceStatus,
                                  String appointmentStatus,
                                  String payOsStatus,
                                  String paymentLinkId,
                                  Long orderCode) {
    }
}
