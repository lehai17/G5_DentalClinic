package com.dentalclinic.controller.patient;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.service.patient.PatientAppointmentService;
import com.dentalclinic.service.patient.PatientDashboardService;
import com.dentalclinic.service.patient.PatientNotificationService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Controller
@RequestMapping("/patient")
public class PatientController {

    private final PatientDashboardService dashboardService;
    private final PatientAppointmentService appointmentService;
    private final PatientNotificationService notificationService;

    public PatientController(
            PatientDashboardService dashboardService,
            PatientAppointmentService appointmentService,
            PatientNotificationService notificationService
    ) {
        this.dashboardService = dashboardService;
        this.appointmentService = appointmentService;
        this.notificationService = notificationService;
    }

    // =========================
    // Dashboard
    // =========================
    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Long patientUserId = 1L;

        var appointments = dashboardService.getPatientAppointments(patientUserId);
        var patient = dashboardService.getPatientProfile(patientUserId);

        model.addAttribute("patient", patient);

        model.addAttribute("appointments", appointments);

        model.addAttribute("notifications",
                dashboardService.getNotifications(patientUserId));

        model.addAttribute("unreadCount",
                dashboardService.countUnreadNotifications(patientUserId));

        // Find nearest upcoming appointment
        LocalDate today = LocalDate.now();
        var nearestAppointment = appointments.stream()
                .filter(apt -> apt.getDate().isAfter(today) || apt.getDate().equals(today))
                .filter(apt -> apt.getStatus() != AppointmentStatus.COMPLETED 
                        && apt.getStatus() != AppointmentStatus.CANCELLED)
                .sorted((a1, a2) -> {
                    int dateCompare = a1.getDate().compareTo(a2.getDate());
                    if (dateCompare != 0) return dateCompare;
                    return a1.getStartTime().compareTo(a2.getStartTime());
                })
                .findFirst()
                .orElse(null);

        model.addAttribute("nearestAppointment", nearestAppointment);

        // Calculate statistics for summary cards
        long pendingSupportCount = 0; // Mock data - can be from support service
        
        // Visit statistics (mock data for 6 months)
        List<Integer> visitStats = List.of(12, 15, 18, 14, 16, 20); // Last 6 months
        model.addAttribute("visitStatistics", visitStats);
        
        model.addAttribute("pendingSupportCount", pendingSupportCount);
        
        // Page metadata
        model.addAttribute("pageTitle", "Bảng điều khiển");
        model.addAttribute("active", "dashboard");

        return "patient/dashboard";
    }


    // =========================
    // View available time slots
    // =========================
    @GetMapping("/appointments/available")
    public String showAvailableSlots(
            @RequestParam(required = false) String date,
            @RequestParam(required = false) Long dentistId,
            Model model
    ) {
        model.addAttribute("dentists", appointmentService.getAllDentists());
        model.addAttribute("selectedDate", date);
        model.addAttribute("selectedDentistId", dentistId);
        
        if (date != null && !date.isEmpty() && dentistId != null) {
            LocalDate selectedDate = LocalDate.parse(date);
            model.addAttribute("timeSlots", 
                    appointmentService.getAvailableTimeSlots(dentistId, selectedDate));
        }
        
        model.addAttribute("pageTitle", "Xem lịch trống");
        model.addAttribute("active", "appointments");
        
        return "patient/appointments/available";
    }

    // =========================
    // View appointment list/history
    // =========================
    @GetMapping("/appointments")
    public String appointmentList(Model model) {
        Long patientUserId = 1L;
        
        List<Appointment> allAppointments = appointmentService.getAppointmentHistory(patientUserId);
        LocalDate today = LocalDate.now();
        
        List<Appointment> upcoming = allAppointments.stream()
                .filter(apt -> apt.getDate().isAfter(today) || apt.getDate().equals(today))
                .filter(apt -> apt.getStatus() != AppointmentStatus.COMPLETED 
                        && apt.getStatus() != AppointmentStatus.CANCELLED)
                .collect(java.util.stream.Collectors.toList());
        
        List<Appointment> past = allAppointments.stream()
                .filter(apt -> apt.getDate().isBefore(today) 
                        || apt.getStatus() == AppointmentStatus.COMPLETED
                        || apt.getStatus() == AppointmentStatus.CANCELLED)
                .collect(java.util.stream.Collectors.toList());
        
        model.addAttribute("upcomingAppointments", upcoming);
        model.addAttribute("pastAppointments", past);
        model.addAttribute("pageTitle", "Lịch hẹn");
        model.addAttribute("active", "appointments");
        
        return "patient/appointments/list";
    }

    // =========================
    // Create appointment form
    // =========================
    @GetMapping("/appointments/create")
    public String showCreateForm(Model model) {
        model.addAttribute("services", appointmentService.getActiveServices());
        model.addAttribute("dentists", appointmentService.getAllDentists());
        model.addAttribute("pageTitle", "Đặt lịch hẹn");
        model.addAttribute("active", "appointments");
        return "patient/appointments/create";
    }

    // =========================
    // Submit appointment
    // =========================
    @PostMapping("/appointments/create")
    public String createAppointment(
            @RequestParam Long dentistId,
            @RequestParam Long serviceId,
            @RequestParam String date,
            @RequestParam String startTime,
            @RequestParam String endTime
    ) {
        Long patientUserId = 1L; // TODO: lấy từ session

        appointmentService.createAppointment(
                patientUserId,
                dentistId,
                serviceId,
                LocalDate.parse(date),
                LocalTime.parse(startTime),
                LocalTime.parse(endTime)
        );

        return "redirect:/patient/appointments";
    }

    // =========================
    // Appointment detail
    // =========================
    @GetMapping("/appointments/{id}")
    public String appointmentDetail(
            @PathVariable Long id,
            Model model
    ) {
        Long patientUserId = 1L;

        Appointment appointment =
                appointmentService.getAppointmentDetail(id, patientUserId);

        model.addAttribute("appointment", appointment);
        model.addAttribute("pageTitle", "Chi tiết lịch hẹn");
        model.addAttribute("active", "appointments");
        return "patient/appointments/detail";
    }

    // =========================
    // View appointment by status
    // =========================
    @GetMapping("/appointments/status/{status}")
    public String viewByStatus(
            @PathVariable AppointmentStatus status,
            Model model
    ) {
        Long patientUserId = 1L;

        List<Appointment> appointments =
                appointmentService.getAppointmentsByStatus(patientUserId, status);

        model.addAttribute("appointments", appointments);
        model.addAttribute("status", status);
        model.addAttribute("pageTitle", "Lịch hẹn - " + status);
        model.addAttribute("active", "appointments");

        return "patient/appointments/list";
    }

    // =========================
    // Notifications
    // =========================
    @GetMapping("/notifications")
    public String notifications(Model model) {
        Long patientUserId = 1L;

        model.addAttribute(
                "notifications",
                notificationService.getNotifications(patientUserId)
        );

        return "patient/notification/list";
    }
}
