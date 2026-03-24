package com.dentalclinic.controller.customer;

import com.dentalclinic.dto.customer.RebookPrefillDto;
import com.dentalclinic.dto.customer.AppointmentDto;
import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerAppointmentService;
import com.dentalclinic.service.customer.CustomerProfileService;
import com.dentalclinic.service.review.ReviewMarketingService;
import com.dentalclinic.service.customer.CustomerVoucherWalletService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.security.oauth2.core.user.OAuth2User;

import java.util.ArrayList;

@Controller
public class CustomerHomepageController {

    private final CustomerProfileService profileService;
    private final ServiceRepository serviceRepo;
    private final DentistProfileRepository dentistRepo;
    private final BlogRepository blogRepo;
    private final UserRepository userRepository;
    private final CustomerAppointmentService customerAppointmentService;
    private final ReviewMarketingService reviewMarketingService;
    private final CustomerVoucherWalletService customerVoucherWalletService;

    public CustomerHomepageController(CustomerProfileService profileService,
                                      ServiceRepository serviceRepo,
                                      DentistProfileRepository dentistRepo,
                                      BlogRepository blogRepo,
                                      UserRepository userRepository,
                                      CustomerAppointmentService customerAppointmentService,
                                      ReviewMarketingService reviewMarketingService,
                                      CustomerVoucherWalletService customerVoucherWalletService) {
        this.profileService = profileService;
        this.serviceRepo = serviceRepo;
        this.dentistRepo = dentistRepo;
        this.blogRepo = blogRepo;
        this.userRepository = userRepository;
        this.customerAppointmentService = customerAppointmentService;
        this.reviewMarketingService = reviewMarketingService;
        this.customerVoucherWalletService = customerVoucherWalletService;
    }

    @GetMapping({"/", "/index", "/home"})
    public String redirectToHomepage() {
        return "redirect:/homepage";
    }

    @GetMapping({"/homepage", "/customer/homepage"})
    public String showHomepage(@RequestParam(defaultValue = "0") int page,
                               Authentication authentication,
                               Model model) {
        model.addAttribute("active", "homepage");
        model.addAttribute("featuredReviews", reviewMarketingService.getHomepageFeaturedReviews());

        Long currentCustomerId = resolveCurrentUserId(authentication);

        try {
            model.addAttribute("services", serviceRepo.findByActiveTrue());
            model.addAttribute("dentists", dentistRepo.filterDentists(null, null, UserStatus.ACTIVE));
            model.addAttribute("voucherService", customerVoucherWalletService);

            int safePage = Math.max(page, 0);
            Pageable pageable = PageRequest.of(safePage, 2);
            Page<Blog> blogPage = blogRepo.findByStatusOrderByApprovedAtDesc(BlogStatus.APPROVED, pageable);
            model.addAttribute("blogs", blogPage.getContent());
            model.addAttribute("currentPage", safePage);
            model.addAttribute("totalPages", blogPage.getTotalPages());

            if (currentCustomerId != null) {
                CustomerProfile profile = profileService.getCurrentCustomerProfile(currentCustomerId);

                if (profile == null) {
                    profile = new CustomerProfile();
                    profile.setFullName("Khách hàng");
                    model.addAttribute("appointments", new ArrayList<>());
                } else {
                    model.addAttribute("appointments", profileService.getCustomerAppointments(profile.getId()));
                }

                model.addAttribute("customer", profile);
                model.addAttribute("voucherBannerVouchers",
                        customerVoucherWalletService.getHomepageBannerVouchers(currentCustomerId));
            } else {
                CustomerProfile guest = new CustomerProfile();
                guest.setFullName("Khách");
                model.addAttribute("customer", guest);
                model.addAttribute("appointments", new ArrayList<>());
                model.addAttribute("voucherBannerVouchers", new ArrayList<>());
            }

            return "customer/homepage";
        } catch (Exception e) {
            CustomerProfile guest = new CustomerProfile();
            guest.setFullName("Khách");

            model.addAttribute("customer", guest);
            model.addAttribute("appointments", new ArrayList<>());
            model.addAttribute("services", new ArrayList<>());
            model.addAttribute("dentists", new ArrayList<>());
            model.addAttribute("voucherBannerVouchers", new ArrayList<>());
            model.addAttribute("voucherService", customerVoucherWalletService);
            model.addAttribute("blogs", new ArrayList<>());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);

            return "customer/homepage";
        }
    }

    @GetMapping("/customer/book")
    public String bookingPage(@RequestParam(required = false) String status,
                              @RequestParam(required = false) Long id,
                              Authentication authentication,
                              Model model) {
        prepareBookingPage(model, null);
        applyBookingReturnStatus(model, resolveCurrentUserId(authentication), status, id);
        return "customer/booking";
    }

    @GetMapping("/customer/appointments/{id}/rebook")
    public String rebookAppointment(@PathVariable("id") Long appointmentId,
                                    Authentication authentication,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        Long currentUserId = resolveCurrentUserId(authentication);
        if (currentUserId == null) {
            return "redirect:/login";
        }

        try {
            RebookPrefillDto rebookPrefill = customerAppointmentService.prepareRebookPrefill(currentUserId, appointmentId);
            prepareBookingPage(model, rebookPrefill);
            model.addAttribute("infoMessage", "Đã điền sẵn thông tin từ lịch hẹn cũ. Vui lòng chọn ngày và giờ khám mới.");
            if (rebookPrefill.getWarningMessage() != null && !rebookPrefill.getWarningMessage().isBlank()) {
                model.addAttribute("warningMessage", rebookPrefill.getWarningMessage());
            }
            return "customer/booking";
        } catch (RuntimeException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/customer/my-appointments";
        }
    }

    @GetMapping("/customer/my-appointments")
    public String appointmentsPage(Model model) {
        model.addAttribute("active", "appointments");
        return "customer/appointments";
    }

    private void prepareBookingPage(Model model, RebookPrefillDto rebookPrefill) {
        model.addAttribute("services", serviceRepo.findByActiveTrue());
        model.addAttribute("active", "booking");
        model.addAttribute("rebookPrefill", rebookPrefill != null ? rebookPrefill : new RebookPrefillDto());
    }

    private void applyBookingReturnStatus(Model model, Long userId, String status, Long appointmentId) {
        if (status == null || status.isBlank()) {
            return;
        }

        String normalizedStatus = status.trim().toLowerCase();
        AppointmentDto appointment = null;
        if (userId != null && appointmentId != null) {
            appointment = customerAppointmentService.getAppointmentDetail(userId, appointmentId).orElse(null);
        }

        boolean verifiedSuccess = "success".equals(normalizedStatus)
                && appointment != null
                && "PENDING".equalsIgnoreCase(appointment.getStatus());

        if (verifiedSuccess) {
            model.addAttribute("bookingReturnType", "success");
            model.addAttribute("bookingReturnTitle", "Đặt lịch thành công");
            model.addAttribute("bookingReturnMessage", "Lịch hẹn của bạn đã được tạo và ghi nhận tiền cọc thành công.");
            model.addAttribute("bookingReturnAppointmentId", appointmentId);
            return;
        }

        if ("fail".equals(normalizedStatus) && appointmentId != null) {
            model.addAttribute("bookingReturnType", "warning");
            model.addAttribute("bookingReturnTitle", "Thanh toán chưa hoàn tất");
            model.addAttribute("bookingReturnMessage", "Thanh toán không thành công hoặc giao dịch không làm thay đổi lịch hẹn.");
            model.addAttribute("bookingReturnAppointmentId", appointmentId);
            return;
        }

        model.addAttribute("bookingReturnType", "info");
        model.addAttribute("bookingReturnTitle", "Không ghi nhận thay đổi");
        model.addAttribute("bookingReturnMessage", "Không ghi nhận kết quả đặt lịch hợp lệ từ liên kết này. Có thể bạn đã mở lại liên kết cũ hoặc đường dẫn không hợp lệ.");
        model.addAttribute("bookingReturnAppointmentId", appointmentId);
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof UserDetails userDetails) {
            return userRepository.findByEmail(userDetails.getUsername())
                    .map(user -> user.getId())
                    .orElse(null);
        }

        if (principal instanceof OAuth2User oauth2User) {
            String email = oauth2User.getAttribute("email");
            if (email != null && !email.isBlank()) {
                return userRepository.findByEmail(email)
                        .map(user -> user.getId())
                        .orElse(null);
            }
        }

        return userRepository.findByEmail(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }
}

