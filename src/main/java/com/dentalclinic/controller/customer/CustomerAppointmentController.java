package com.dentalclinic.controller.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateAppointmentRequest;
import com.dentalclinic.dto.customer.RescheduleAppointmentRequest;
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
        if (userId == null || !userRepository.existsById(userId)) return null;
        return userId;
    }

    @GetMapping("/slots")
    public ResponseEntity<?> getSlots(@RequestParam(required = false) Long serviceId,
                                      @RequestParam(required = false) String date,
                                      HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        LocalDate parsedDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();
        List<SlotDto> slots = customerAppointmentService.getAvailableSlots(userId, serviceId, parsedDate);
        return ResponseEntity.ok(slots);
    }

    @GetMapping("/slots/all")
    public ResponseEntity<?> getAllSlots(@RequestParam String date, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        LocalDate parsedDate = LocalDate.parse(date);
        List<SlotDto> slots = customerAppointmentService.getAllSlotsForDate(parsedDate);
        return ResponseEntity.ok(slots);
    }

    @PostMapping("/appointments")
    public ResponseEntity<?> createAppointment(@Valid @RequestBody CreateAppointmentRequest request, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        AppointmentDto created = customerAppointmentService.createAppointment(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/appointments/{id}/confirm")
    public ResponseEntity<?> confirmAppointment(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        AppointmentDto confirmed = customerAppointmentService.confirmAppointment(id);
        return ResponseEntity.ok(confirmed);
    }

    @GetMapping("/appointments")
    public ResponseEntity<?> getMyAppointments(HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        List<AppointmentDto> list = customerAppointmentService.getMyAppointments(userId);
        return ResponseEntity.ok(list);
    }

    @GetMapping("/appointments/{id}")
    public ResponseEntity<?> getAppointmentDetail(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        Optional<AppointmentDto> detail = customerAppointmentService.getAppointmentDetail(userId, id);
        return detail.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/appointments/{id}/checkin")
    public ResponseEntity<?> checkIn(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        Optional<AppointmentDto> result = customerAppointmentService.checkIn(userId, id);
        if (result.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "CHECKIN_NOT_ALLOWED", "message", "Check-in not allowed."));
        }
        return ResponseEntity.ok(result.get());
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        AppointmentDto result = customerAppointmentService.cancelAppointment(userId, id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/appointments/{id}/reschedule")
    public ResponseEntity<?> reschedule(@PathVariable Long id,
                                        @Valid @RequestBody RescheduleAppointmentRequest request,
                                        HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        AppointmentDto result = customerAppointmentService.rescheduleAppointment(userId, id, request);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/debug/slots")
    public ResponseEntity<?> debugGetAllSlots(@RequestParam String date, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        LocalDate parsedDate = LocalDate.parse(date);
        List<SlotDto> slots = customerAppointmentService.getAllSlotsForDate(parsedDate);
        return ResponseEntity.ok(Map.of(
                "date", parsedDate,
                "totalSlots", slots.size(),
                "slots", slots
        ));
    }
}
