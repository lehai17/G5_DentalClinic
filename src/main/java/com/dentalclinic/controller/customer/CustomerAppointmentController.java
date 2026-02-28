package com.dentalclinic.controller.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
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
    private final UserRepository userRepository;

    public CustomerAppointmentController(CustomerAppointmentService customerAppointmentService,
                                         UserRepository userRepository) {
        this.customerAppointmentService = customerAppointmentService;
        this.userRepository = userRepository;
    }

    private Long getCurrentUserId(HttpSession session) {
        Object uid = session.getAttribute(SESSION_USER_ID);
        Long userId = null;
        if (uid instanceof Long) userId = (Long) uid;
        else if (uid instanceof Number) userId = ((Number) uid).longValue();
        if (userId == null) return null;
        if (!userRepository.existsById(userId)) return null;
        return userId;
    }

    /**
     * GET /customer/slots?serviceId=&date=yyyy-MM-dd
     * Returns available slots that have enough consecutive slots for the service duration.
     * Includes overlap status for the authenticated user.
     */
    @GetMapping("/slots")
    public ResponseEntity<?> getSlots(
            @RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) String date,
            HttpSession session) {

        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        LocalDate parsedDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        List<SlotDto> slots = customerAppointmentService.getAvailableSlotsWithOverlapStatus(userId, serviceId, parsedDate);
        return ResponseEntity.ok(slots);
    }

    /**
     * GET /customer/slots/all?date=yyyy-MM-dd
     * Returns all slots for a date with their availability status.
     */
    @GetMapping("/slots/all")
    public ResponseEntity<?> getAllSlots(
            @RequestParam String date,
            HttpSession session) {

        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        LocalDate parsedDate = LocalDate.parse(date);
        List<SlotDto> slots = customerAppointmentService.getAllSlotsForDate(parsedDate);
        return ResponseEntity.ok(slots);
    }

    /**
     * POST /customer/appointments
     * Creates a new appointment with PENDING status.
     * After successful payment, the appointment should be confirmed.
     */
    @PostMapping("/appointments")
    public ResponseEntity<?> createAppointment(@Valid @RequestBody CreateAppointmentRequest request, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            AppointmentDto created = customerAppointmentService.createAppointment(userId, request);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (com.dentalclinic.exception.BusinessException e) {
            // business-rule violation - treat as bad request
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalArgumentException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Dữ liệu không hợp lệ.";
            if (msg.contains("User not found") || msg.contains("đăng nhập")) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("error", "Phiên đăng nhập không hợp lệ. Vui lòng đăng nhập lại."));
            }
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    /**
     * POST /customer/appointments/{id}/confirm
     * Confirm appointment after successful payment.
     */
    @PostMapping("/appointments/{id}/confirm")
    public ResponseEntity<?> confirmAppointment(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        try {
            AppointmentDto confirmed = customerAppointmentService.confirmAppointment(id);
            return ResponseEntity.ok(confirmed);
        } catch (IllegalArgumentException | IllegalStateException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Không thể xác nhận lịch hẹn.";
            return ResponseEntity.badRequest().body(Map.of("error", msg));
        }
    }

    /**
     * GET /customer/appointments
     */
    @GetMapping("/appointments")
    public ResponseEntity<?> getMyAppointments(HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        List<AppointmentDto> list = customerAppointmentService.getMyAppointments(userId);
        return ResponseEntity.ok(list);
    }

    /**
     * GET /customer/appointments/{id}
     */
    @GetMapping("/appointments/{id}")
    public ResponseEntity<?> getAppointmentDetail(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        
        Optional<AppointmentDto> detail = customerAppointmentService.getAppointmentDetail(userId, id);
        return detail
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * POST /customer/appointments/{id}/checkin
     */
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

    /**
     * POST /customer/appointments/{id}/cancel
     */
    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        
        try {
            AppointmentDto result = customerAppointmentService.cancelAppointment(userId, id);
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DEBUG ENDPOINT: /customer/debug/slots?date=yyyy-MM-dd
     * Returns RAW slots from database (no filtering)
     * Use this to verify database has full slots
     */
    @GetMapping("/debug/slots")
    public ResponseEntity<?> debugGetAllSlots(
            @RequestParam String date,
            HttpSession session) {
        
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }

        LocalDate parsedDate = LocalDate.parse(date);
        
        // Get ALL slots including FULL ones
        List<SlotDto> slots = customerAppointmentService.getAllSlotsForDate(parsedDate);
        
        return ResponseEntity.ok(Map.of(
            "date", parsedDate,
            "totalSlots", slots.size(),
            "slots", slots,
            "note", "This shows ALL slots (including FULL ones). If count < 18, database is incomplete."
        ));
    }
}
