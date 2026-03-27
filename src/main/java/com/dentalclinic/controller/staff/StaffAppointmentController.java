package com.dentalclinic.controller.staff;

import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.dto.customer.CreateWalkInAppointmentRequest;
import com.dentalclinic.dto.customer.SlotDto;
import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionStatus;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.service.mail.EmailService;
import com.dentalclinic.service.staff.StaffAppointmentService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/staff")
public class StaffAppointmentController {

    @Autowired
    private StaffAppointmentService staffAppointmentService;

    @Autowired
    private EmailService emailService;

    @Autowired
    private ServiceRepository serviceRepository;

    @GetMapping("/dashboard")
    public String dashboard(@RequestParam(required = false, defaultValue = "today") String view,
            Model model) {

        var appointments = staffAppointmentService.getAllAppointments();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today;
        LocalDate endDate = today;

        // Xác định khoảng thời gian
        switch (view) {
            case "week" -> {
                startDate = today.with(DayOfWeek.MONDAY);
                endDate = today.with(DayOfWeek.SUNDAY);
            }
            case "month" -> {
                startDate = today.withDayOfMonth(1);
                endDate = today.withDayOfMonth(today.lengthOfMonth());
            }
            default -> {
                // today
                startDate = today;
                endDate = today;
            }
        }

        final LocalDate fromDate = startDate;
        final LocalDate toDate = endDate;

        var filtered = appointments.stream()
                .filter(a -> !a.getDate().isBefore(fromDate)
                        && !a.getDate().isAfter(toDate))
                .toList();

        model.addAttribute("pageTitle", "Dashboard");
        model.addAttribute("staffName", "Staff");

        model.addAttribute("view", view);

        model.addAttribute("totalCount", filtered.size());
        model.addAttribute(
                "pendingCount",
                filtered.stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.PENDING)
                        .count());
        model.addAttribute(
                "completedCount",
                filtered.stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.COMPLETED)
                        .count());
        model.addAttribute(
                "cancelledCount",
                filtered.stream()
                        .filter(a -> a.getStatus() == AppointmentStatus.CANCELLED)
                        .count());

        return "staff/dashboard";
    }

    @GetMapping("/appointments")
    public String appointments(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "") String serviceKeyword,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            Model model) {

        model.addAttribute("pageTitle", "Quản lý lịch hẹn");
        model.addAttribute("staffName", "Staff");

        Page<Appointment> appointmentPage = staffAppointmentService.searchAndSort(keyword, serviceKeyword, sort, page);
        List<Appointment> appointments = appointmentPage.getContent();

        model.addAttribute("appointments", appointments);
        model.addAttribute("serviceSummaries", staffAppointmentService.buildServiceSummaries(appointments));
        model.addAttribute("dentistLeaveFlags", staffAppointmentService.buildDentistLeaveFlags(appointments));
        model.addAttribute("staffCancelableFlags", staffAppointmentService.buildStaffCancelableFlags(appointments));

        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", appointmentPage.getTotalPages());

        model.addAttribute("keyword", keyword);
        model.addAttribute("serviceKeyword", serviceKeyword);
        model.addAttribute("sort", sort);

        return "staff/appointments";
    }

    @GetMapping("/wallet/withdraw-requests")
    public String walletWithdrawRequests(@RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "PENDING") String status,
            Model model) {
        WalletTransactionStatus selectedStatus = resolveWithdrawStatus(status);
        List<WalletTransaction> requests = staffAppointmentService.getWithdrawalRequestsByStatus(selectedStatus);
        int pageSize = 3;
        int totalItems = requests.size();
        int totalPages = Math.max(1, (int) Math.ceil((double) totalItems / pageSize));
        int currentPage = Math.min(Math.max(page, 1), totalPages);
        int fromIndex = Math.min((currentPage - 1) * pageSize, totalItems);
        int toIndex = Math.min(fromIndex + pageSize, totalItems);

        model.addAttribute("pageTitle", "Yeu cau rut tien vi");
        model.addAttribute("staffName", "Staff");
        model.addAttribute("withdrawRequests", requests.subList(fromIndex, toIndex));
        model.addAttribute("selectedStatus", selectedStatus.name());
        model.addAttribute("currentPage", currentPage);
        model.addAttribute("totalPages", totalPages);
        return "staff/wallet-withdraw-requests";
    }

    @GetMapping("/walk-in-booking")
    public String walkInBookingPage(Model model) {
        model.addAttribute("pageTitle", "Dat lich khach vang lai");
        model.addAttribute("staffName", "Staff");
        model.addAttribute("services", serviceRepository.findByActiveTrue());
        return "staff/walk-in-booking";
    }

    @GetMapping("/walk-in/slots")
    @ResponseBody
    public ResponseEntity<?> getWalkInSlots(@RequestParam(required = false) Long serviceId,
            @RequestParam(required = false) List<Long> serviceIds,
            @RequestParam String date) {
        LocalDate parsedDate = LocalDate.parse(date);
        List<Long> resolvedServiceIds = new ArrayList<>();
        if (serviceIds != null) {
            resolvedServiceIds.addAll(serviceIds);
        }
        if (serviceId != null) {
            resolvedServiceIds.add(serviceId);
        }
        List<SlotDto> slots = staffAppointmentService.getWalkInAvailableSlots(resolvedServiceIds, parsedDate);
        return ResponseEntity.ok(slots);
    }

    @GetMapping("/walk-in/slots/all")
    @ResponseBody
    public ResponseEntity<?> getAllWalkInSlots(@RequestParam String date) {
        return ResponseEntity.ok(staffAppointmentService.getAllWalkInSlotsForDate(LocalDate.parse(date)));
    }

    @PostMapping("/walk-in/appointments")
    @ResponseBody
    public ResponseEntity<?> createWalkInAppointment(@Valid @RequestBody CreateWalkInAppointmentRequest request) {
        try {
            AppointmentDto created = staffAppointmentService.createWalkInAppointment(request);
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("id", created.getId());
            payload.put("customerName", created.getCustomerName());
            payload.put("serviceName", created.getServiceName());
            payload.put("date", created.getDate());
            payload.put("startTime", created.getStartTime());
            payload.put("endTime", created.getEndTime());
            payload.put("status", created.getStatus());
            return ResponseEntity.status(201).body(payload);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage() != null ? e.getMessage() : "Khong the tao lich hen."));
        }
    }

    @PostMapping("/appointments/confirm")
    @ResponseBody
    public void confirm(@RequestParam Long id) {
        staffAppointmentService.confirmAppointment(id);
    }

    @PostMapping("/appointments/assign")
    @ResponseBody
    public ResponseEntity<?> assign(
            @RequestParam Long appointmentId,
            @RequestParam Long dentistId) {

        try {
            staffAppointmentService.assignDentist(appointmentId, dentistId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/appointments/complete")
    @ResponseBody
    public void complete(@RequestParam Long id) {
        staffAppointmentService.completeAppointment(id);
    }

    @PostMapping("/appointments/cancel")
    @ResponseBody
    public void cancel(
            @RequestParam Long id,
            @RequestParam String reason) {
        staffAppointmentService.cancelAppointmentByStaffWithRules(id, reason);
    }

    @PostMapping("/appointments/cancel-system")
    @ResponseBody
    public void cancelSystem(
            @RequestParam Long id,
            @RequestParam String reason) {
        staffAppointmentService.cancelAppointment(id, reason);
    }

    @PostMapping("/appointments/checkin")
    @ResponseBody
    public void checkin(@RequestParam Long id) {
        staffAppointmentService.checkInAppointment(id);
    }

    @GetMapping("/appointments/available-dentists") // Thêm /appointments vào đây
    @ResponseBody
    public ResponseEntity<List<DentistProfile>> getAvailableDentists(@RequestParam Long appointmentId) {
        List<DentistProfile> availableDentists = staffAppointmentService
                .getAvailableDentistsForAppointment(appointmentId);
        return ResponseEntity.ok(availableDentists);
    }

    @GetMapping("/appointments/{id}/invoice-preview")
    @ResponseBody
    public ResponseEntity<?> invoicePreview(@PathVariable Long id) {
        try {
            AppointmentDto preview = staffAppointmentService.getInvoicePreview(id);
            return ResponseEntity.ok(preview);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/appointments/{id}/payment-options")
    @ResponseBody
    public ResponseEntity<?> paymentOptions(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(staffAppointmentService.preparePaymentOptions(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/appointments/{id}/pay-wallet")
    @ResponseBody
    public ResponseEntity<?> payWithWallet(@PathVariable Long id) {
        try {
            Appointment appointment = staffAppointmentService.payWithWalletByStaff(id);
            return ResponseEntity.ok(appointment.getStatus().name());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/appointments/{id}/confirm-manual-payment")
    @ResponseBody
    public ResponseEntity<?> confirmManualPayment(@PathVariable Long id, @RequestParam BigDecimal paidAmount) {
        try {
            Appointment appointment = staffAppointmentService.confirmManualPayment(id, paidAmount);
            return ResponseEntity.ok(appointment.getStatus().name());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/appointments/{id}/payos-link")
    @ResponseBody
    public ResponseEntity<?> createPayOsLink(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(staffAppointmentService.createPayOsQr(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/appointments/{id}/payos-status")
    @ResponseBody
    public ResponseEntity<?> payOsStatus(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(staffAppointmentService.getPayOsPaymentStatus(id));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/appointments/process-payment")
    @ResponseBody
    public ResponseEntity<?> processPayment(@RequestParam Long id) {
        try {
            staffAppointmentService.processPayment(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/wallet/withdraw-requests/{id}/approve")
    @ResponseBody
    public ResponseEntity<?> approveWithdrawRequest(@PathVariable Long id,
            @RequestParam("proofImage") MultipartFile proofImage) {
        try {
            WalletTransaction transaction = staffAppointmentService.approveWithdrawalRequest(id, proofImage);
            return ResponseEntity.ok(Map.of(
                    "id", transaction.getId(),
                    "status", transaction.getStatus().name(),
                    "proofImageUrl", transaction.getProofImageUrl()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/wallet/withdraw-requests/{id}/reject")
    @ResponseBody
    public ResponseEntity<?> rejectWithdrawRequest(@PathVariable Long id, @RequestParam String reason) {
        try {
            WalletTransaction transaction = staffAppointmentService.rejectWithdrawalRequest(id, reason);
            return ResponseEntity.ok(Map.of(
                    "id", transaction.getId(),
                    "status", transaction.getStatus().name()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    private WalletTransactionStatus resolveWithdrawStatus(String status) {
        if (status == null || status.isBlank()) {
            return WalletTransactionStatus.PENDING;
        }

        try {
            WalletTransactionStatus parsed = WalletTransactionStatus.valueOf(status.trim().toUpperCase());
            if (parsed == WalletTransactionStatus.COMPLETED
                    || parsed == WalletTransactionStatus.CANCELLED
                    || parsed == WalletTransactionStatus.PENDING) {
                return parsed;
            }
        } catch (IllegalArgumentException ignored) {
        }

        return WalletTransactionStatus.PENDING;
    }
}
