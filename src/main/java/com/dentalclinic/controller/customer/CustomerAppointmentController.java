package com.dentalclinic.controller.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/customer")
public class CustomerAppointmentController {

    private static final String SESSION_USER_ID = "userId";

    private final CustomerAppointmentService customerAppointmentService;

    public CustomerAppointmentController(CustomerAppointmentService customerAppointmentService) {
        this.customerAppointmentService = customerAppointmentService;
    }

    /** Lấy userId từ session (được set khi CUSTOMER đăng nhập). Chưa đăng nhập → null → API trả 401. */
    private Long getCurrentUserId(HttpSession session) {
        Object uid = session.getAttribute(SESSION_USER_ID);
        if (uid instanceof Long) return (Long) uid;
        if (uid instanceof Number) return ((Number) uid).longValue();
        return null;
    }

    /** GET /customer/slots?serviceId=&dentistId=&date=yyyy-MM-dd */
    @GetMapping("/slots")
    public ResponseEntity<?> getSlots(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) Long dentistId,
            @RequestParam(required = false) String date,
            HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        LocalDate parsedDate = date != null && !date.isBlank() ? LocalDate.parse(date) : LocalDate.now();
        List<SlotDto> slots = customerAppointmentService.getAvailableSlots(serviceId, dentistId, parsedDate);
        return ResponseEntity.ok(slots);
    }

    /** POST /customer/appointments */
    @PostMapping("/appointments")
    public ResponseEntity<?> createAppointment(@Valid @RequestBody CreateAppointmentRequest request, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        try {
            AppointmentDto created = customerAppointmentService.createAppointment(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** GET /customer/appointments */
    @GetMapping("/appointments")
    public ResponseEntity<?> getMyAppointments(HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        List<AppointmentDto> list = customerAppointmentService.getMyAppointments(userId);
        return ResponseEntity.ok(list);
    }

    /** GET /customer/appointments/{id} */
    @GetMapping("/appointments/{id}")
    public ResponseEntity<?> getAppointmentDetail(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        return customerAppointmentService.getAppointmentDetail(userId, id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /customer/appointments/{id}/checkin */
    @PostMapping("/appointments/{id}/checkin")
    public ResponseEntity<?> checkIn(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        Optional<AppointmentDto> result = customerAppointmentService.checkIn(userId, id);
        if (result.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Check-in not allowed (wrong date or status)"));
        }
        return ResponseEntity.ok(result.get());
    }
}
