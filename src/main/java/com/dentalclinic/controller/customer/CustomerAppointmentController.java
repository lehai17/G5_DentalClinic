package com.dentalclinic.controller.customer;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateReviewRequest;
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
import java.util.ArrayList;
import java.util.HashMap;
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
                                      @RequestParam(required = false) List<Long> serviceIds,
                                      @RequestParam(required = false) String date,
                                      HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        LocalDate parsedDate = (date != null && !date.isBlank()) ? LocalDate.parse(date) : LocalDate.now();

        List<Long> resolvedServiceIds = new ArrayList<>();
        if (serviceIds != null) {
            resolvedServiceIds.addAll(serviceIds);
        }
        if (serviceId != null) {
            resolvedServiceIds.add(serviceId);
        }

        List<SlotDto> slots = resolvedServiceIds.isEmpty()
                ? List.of()
                : customerAppointmentService.getAvailableSlots(userId, resolvedServiceIds, parsedDate);
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
    public ResponseEntity<?> getMyAppointments(@RequestParam(required = false) Integer page,
                                               @RequestParam(required = false) Integer size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false, defaultValue = "date_desc") String sort,
                                               @RequestParam(required = false, defaultValue = "default") String view,
                                               HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        String normalizedSort = customerAppointmentService.normalizeAppointmentSort(sort);
        String normalizedView = customerAppointmentService.normalizeAppointmentView(view);

        if (page != null || size != null || (keyword != null && !keyword.isBlank()) || (sort != null && !sort.isBlank()) || (view != null && !view.isBlank())) {
            int p = page == null ? 0 : page;
            int s = size == null ? 5 : size;
            var resultPage = customerAppointmentService.getMyAppointmentsPage(userId, p, s, keyword, normalizedSort, normalizedView);
            return ResponseEntity.ok(Map.of(
                    "content", resultPage.getContent(),
                    "page", resultPage.getNumber(),
                    "size", resultPage.getSize(),
                    "totalPages", resultPage.getTotalPages(),
                    "totalElements", resultPage.getTotalElements(),
                    "sort", normalizedSort,
                    "view", normalizedView
            ));
        }

        List<AppointmentDto> list = customerAppointmentService.getMyAppointments(userId, normalizedView);
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
        try {
            customerAppointmentService.checkIn(userId, id);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CHECKIN_NOT_ALLOWED",
                    "message", "Luồng hiện tại không sử dụng bước check-in."
            ));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CHECKIN_NOT_ALLOWED",
                    "message", ex.getMessage() != null ? ex.getMessage() : "Check-in not allowed."
            ));
        }
    }

    @PostMapping("/appointments/{id}/cancel")
    public ResponseEntity<?> cancel(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        AppointmentDto result = customerAppointmentService.cancelAppointment(userId, id);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/appointments/{id}/hide")
    public ResponseEntity<?> hideCancelledAppointment(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        customerAppointmentService.hideCancelledAppointmentFromHistory(userId, id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Đã ẩn lịch hẹn đã hủy khỏi danh sách chính."));
    }

    @PostMapping("/appointments/{id}/review")
    public ResponseEntity<?> createReview(@PathVariable Long id,
                                          @Valid @RequestBody CreateReviewRequest request,
                                          HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        var review = customerAppointmentService.createReview(userId, id, request);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Đã gửi đánh giá bác sĩ và dịch vụ thành công.",
                "dentistRating", review.getDentistRating(),
                "serviceRating", review.getServiceRating(),
                "comment", review.getComment() == null ? "" : review.getComment()
        ));
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

    @GetMapping("/appointments/detail/{id}")
    public ResponseEntity<?> getAppointmentDetailForSuccess(@PathVariable Long id, HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));

        // Sử dụng service hiện có của bạn để lấy dữ liệu (đảm bảo tính bảo mật theo userId)
        Optional<AppointmentDto> detail = customerAppointmentService.getAppointmentDetail(userId, id);

        return detail.map(dto -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", dto.getId());
            data.put("serviceName", dto.getServiceName());
            data.put("serviceIds", dto.getServiceIds());
            data.put("services", dto.getServices());
            data.put("totalDurationMinutes", dto.getTotalDurationMinutes());
            data.put("totalAmount", dto.getTotalAmount());
            data.put("depositAmount", dto.getDepositAmount());
            data.put("billedTotal", dto.getBilledTotal());
            data.put("remainingAmount", dto.getRemainingAmount());
            data.put("invoiceId", dto.getInvoiceId());
            data.put("invoiceStatus", dto.getInvoiceStatus());
            data.put("canPayRemaining", dto.isCanPayRemaining());
            data.put("invoiceItems", dto.getInvoiceItems());
            data.put("billingNoteId", dto.getBillingNoteId());
            data.put("billingNoteNote", dto.getBillingNoteNote());
            data.put("billingNoteUpdatedAt", dto.getBillingNoteUpdatedAt());
            data.put("prescriptionItems", dto.getPrescriptionItems());
            data.put("date", dto.getDate().toString());
            data.put("startTime", dto.getStartTime());
            data.put("endTime", dto.getEndTime());
            data.put("dentistName", dto.getDentistName() != null ? dto.getDentistName() : "Sẽ được gán sau");
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

}
