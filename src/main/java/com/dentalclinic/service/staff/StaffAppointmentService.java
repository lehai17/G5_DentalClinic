package com.dentalclinic.service.staff;

import com.dentalclinic.model.appointment.Appointment;
import com.dentalclinic.model.appointment.AppointmentStatus;
import com.dentalclinic.repository.AppointmentRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.model.profile.DentistProfile;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import com.dentalclinic.service.dentist.ReexamService;
import com.dentalclinic.service.mail.EmailService;
import com.dentalclinic.service.notification.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class StaffAppointmentService {

    private static final Logger logger = LoggerFactory.getLogger(StaffAppointmentService.class);

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DentistProfileRepository dentistProfileRepository;

    @Autowired
    private EmailService emailService;

    @Autowired
    private CustomerAppointmentService customerAppointmentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private ReexamService reexamService;

    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Transactional
    public void confirmAppointment(Long appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (appointment.getDentist() == null) {
            throw new RuntimeException("Phải gán bác sĩ trước khi xác nhận");
        }

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Only PENDING appointment can be confirmed");
        }

        appointment.setStatus(AppointmentStatus.CONFIRMED);
        appointmentRepository.save(appointment);

        emailService.sendAppointmentConfirmed(appointment);
    }

    @Transactional
    public void assignDentist(Long appointmentId, Long dentistId) {
        // 1. Tìm lịch hẹn
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        Long oldDentistId = appt.getDentist() != null ? appt.getDentist().getId() : null;

        // 2. Kiểm tra trùng lịch của bác sĩ mới
        boolean hasOverlap = appointmentRepository.hasOverlappingAppointment(
                dentistId,
                appt.getDate(),
                appt.getStartTime(),
                appt.getEndTime()
        );

        if (hasOverlap) {
            throw new RuntimeException("Bác sĩ đã có lịch trong khung giờ n� y");
        }

        // 3. Tìm thông tin bác sĩ
        DentistProfile dentist = dentistProfileRepository.findById(dentistId)
                .orElseThrow(() -> new RuntimeException("Dentist not found"));

        // 4. Cập nhật bác sĩ phụ trách
        appt.setDentist(dentist);

        // 5. TỰ ĐỘNG CHUYỂN TR� NG THÁI: Nếu Ä‘ang PENDING thì chuyển sang CONFIRMED
        if (appt.getStatus() == AppointmentStatus.PENDING) {
            appt.setStatus(AppointmentStatus.CONFIRMED);
        }

        // 6. Lưu thay đổi
        Appointment saved = appointmentRepository.save(appt);

        // 7. Gửi Email xác nhận cho khách h� ng (Tận dụng h� m confirm đã có của bạn)
        try {
            emailService.sendAppointmentConfirmed(saved);
        } catch (Exception e) {
            // Log lỗi gửi mail nhưng không l� m rollback giao dịch gán bác sĩ
            System.err.println("Lỗi gửi email xác nhận: " + e.getMessage());
        }

        // 8. Thông báo hệ thống
        if (oldDentistId == null || !oldDentistId.equals(dentistId)) {
            notificationService.notifyBookingUpdated(saved, "Đã gán bác sĩ v�  xác nhận lịch hẹn th� nh công");
        }
    }

    @Transactional
    public void completeAppointment(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (a.getStatus() != AppointmentStatus.CHECKED_IN) {
            throw new RuntimeException("Only CHECKED_IN appointment can be completed");
        }

        a.setStatus(AppointmentStatus.COMPLETED);
        appointmentRepository.save(a);
        appointmentRepository.flush();  // Force flush to DB
        
        // Auto-confirm any pending reexams for this appointment
        logger.info("[STAFF] Appointment {} status changed to COMPLETED, attempting to auto-confirm reexam", a.getId());
        try {
            // Debug: check database directly
            var debugData = appointmentRepository.debugFindReexamByOriginalId(a.getId());
            logger.info("[STAFF] Debug: Found {} reexam records for appointment ID: {}", debugData.size(), a.getId());
            for (Object[] row : debugData) {
                logger.info("[STAFF] Debug: id={}, original_appointment_id={}, status={}", row[0], row[1], row[2]);
            }
            
            // Find reexam for this appointment
            var reexamOpt = appointmentRepository.findReexamByOriginalAppointmentId(a.getId());
            if (reexamOpt.isPresent()) {
                Appointment reexam = reexamOpt.get();
                logger.info("[STAFF] Found reexam ID: {} with status: {}", reexam.getId(), reexam.getStatus());
                
                if (reexam.getStatus() == AppointmentStatus.REEXAM) {
                    logger.info("[STAFF] Updating reexam ID: {} from REEXAM to CONFIRMED", reexam.getId());
                    reexam.setStatus(AppointmentStatus.CONFIRMED);
                    appointmentRepository.save(reexam);
                    appointmentRepository.flush();
                    logger.info("[STAFF] Reexam ID: {} successfully updated to CONFIRMED", reexam.getId());
                } else {
                    logger.info("[STAFF] Reexam ID: {} has status: {}, not updating", reexam.getId(), reexam.getStatus());
                }
            } else {
                logger.info("[STAFF] No reexam found for appointment ID: {}", a.getId());
            }
        } catch (Exception e) {
            logger.error("[STAFF] Error auto-confirming reexam", e);
            e.printStackTrace();
        }
    }

    @Transactional
    public void cancelAppointment(Long appointmentId, String reason) {
        customerAppointmentService.cancelAppointmentByStaff(appointmentId, reason);
    }

    public Page<Appointment> searchAndSort(
            String keyword,
            String serviceKeyword,
            String sort,
            int page
    ) {
        Sort s = Sort.by("date").ascending();

        if ("newest".equals(sort)) {
            s = Sort.by("date").descending();
        } else if ("oldest".equals(sort)) {
            s = Sort.by("date").ascending();
        }

        Pageable pageable = PageRequest.of(page, 3, s);

        boolean hasCustomer = keyword != null && !keyword.trim().isEmpty();
        boolean hasService = serviceKeyword != null && !serviceKeyword.trim().isEmpty();

        if (hasCustomer && hasService) {
            return appointmentRepository
                    .findByCustomer_FullNameContainingIgnoreCaseAndService_NameContainingIgnoreCase(
                            keyword, serviceKeyword, pageable);
        }

        if (hasCustomer) {
            return appointmentRepository
                    .findByCustomer_FullNameContainingIgnoreCase(keyword, pageable);
        }

        if (hasService) {
            return appointmentRepository
                    .findByService_NameContainingIgnoreCase(serviceKeyword, pageable);
        }

        return appointmentRepository.findAll(pageable);
    }
    public void checkInAppointment(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (a.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new RuntimeException("Only CONFIRMED appointment can be checked-in");
        }

        a.setStatus(AppointmentStatus.CHECKED_IN);
        appointmentRepository.save(a);
    }

    public List<DentistProfile> getAvailableDentistsForAppointment(Long appointmentId) {
        // 1. Tìm thông tin cuộc hẹn để biết ng� y khách đặt
        Appointment appt = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        // 2. Lấy bác sĩ ACTIVE và không nghỉ được duyệt trong ngày hẹn
        return dentistProfileRepository.findAvailableDentistsForDate(appt.getDate());
    }

    @Transactional
    public void processPayment(Long id) {
        Appointment a = appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));

        if (a.getStatus() != AppointmentStatus.DONE) {
            throw new RuntimeException("Chỉ lịch hẹn có trạng thái DONE mới có thể tiến hành thanh toán.");
        }

        a.setStatus(AppointmentStatus.WAITING_PAYMENT);
        appointmentRepository.save(a);
    }
}

