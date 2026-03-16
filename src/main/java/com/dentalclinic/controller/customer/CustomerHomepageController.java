package com.dentalclinic.controller.customer;

import com.dentalclinic.dto.customer.RebookPrefillDto;
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

import java.util.ArrayList;

@Controller
public class CustomerHomepageController {

    private final CustomerProfileService profileService;
    private final ServiceRepository serviceRepo;
    private final DentistProfileRepository dentistRepo;
    private final BlogRepository blogRepo;
    private final UserRepository userRepository;
    private final CustomerAppointmentService customerAppointmentService;

    public CustomerHomepageController(CustomerProfileService profileService,
                                      ServiceRepository serviceRepo,
                                      DentistProfileRepository dentistRepo,
                                      BlogRepository blogRepo,
                                      UserRepository userRepository,
                                      CustomerAppointmentService customerAppointmentService) {
        this.profileService = profileService;
        this.serviceRepo = serviceRepo;
        this.dentistRepo = dentistRepo;
        this.blogRepo = blogRepo;
        this.userRepository = userRepository;
        this.customerAppointmentService = customerAppointmentService;
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
        Long currentCustomerId = resolveCurrentUserId(authentication);
        if (currentCustomerId == null) {
            return "redirect:/login";
        }

        try {
            CustomerProfile profile = profileService.getCurrentCustomerProfile(currentCustomerId);
            if (profile == null) {
                profile = new CustomerProfile();
                profile.setFullName("Khách hàng");
                model.addAttribute("appointments", new ArrayList<>());
            } else {
                model.addAttribute("appointments", profileService.getCustomerAppointments(profile.getId()));
            }
            model.addAttribute("customer", profile);

            model.addAttribute("services", serviceRepo.findByActiveTrue());
            model.addAttribute("dentists", dentistRepo.filterDentists(null, null, UserStatus.ACTIVE));

            int safePage = Math.max(page, 0);
            Pageable pageable = PageRequest.of(safePage, 2);
            Page<Blog> blogPage = blogRepo.findByStatusOrderByApprovedAtDesc(BlogStatus.APPROVED, pageable);
            model.addAttribute("blogs", blogPage.getContent());
            model.addAttribute("currentPage", safePage);
            model.addAttribute("totalPages", blogPage.getTotalPages());

            return "customer/homepage";
        } catch (Exception e) {
            model.addAttribute("customer", new CustomerProfile());
            model.addAttribute("appointments", new ArrayList<>());
            model.addAttribute("services", new ArrayList<>());
            model.addAttribute("dentists", new ArrayList<>());
            model.addAttribute("blogs", new ArrayList<>());
            model.addAttribute("currentPage", 0);
            model.addAttribute("totalPages", 0);
            return "customer/homepage";
        }
    }

    @GetMapping("/customer/book")
    public String bookingPage(Model model) {
        prepareBookingPage(model, null);
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

        return userRepository.findByEmail(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }
}

