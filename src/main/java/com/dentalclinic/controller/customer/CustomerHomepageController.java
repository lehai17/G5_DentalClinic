package com.dentalclinic.controller.customer;

import com.dentalclinic.model.blog.Blog;
import com.dentalclinic.model.blog.BlogStatus;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.BlogRepository;
import com.dentalclinic.repository.DentistProfileRepository;
import com.dentalclinic.repository.ServiceRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;

@Controller
public class CustomerHomepageController {

    private final CustomerProfileService profileService;
    private final ServiceRepository serviceRepo;
    private final DentistProfileRepository dentistRepo;
    private final BlogRepository blogRepo;
    private final UserRepository userRepository;

    // Chỉ giữ lại một Constructor duy nhất
    public CustomerHomepageController(
            CustomerProfileService profileService,
            ServiceRepository serviceRepo,
            DentistProfileRepository dentistRepo,
            BlogRepository blogRepo,
            UserRepository userRepository
    ) {
        this.profileService = profileService;
        this.serviceRepo = serviceRepo;
        this.dentistRepo = dentistRepo;
        this.blogRepo = blogRepo;
        this.userRepository = userRepository;
    }

    @GetMapping({"/", "/index", "/home"})
    public String redirectToHomepage() {
        return "redirect:/homepage";
    }

    @GetMapping({"/homepage", "/customer/homepage"})
    public String showHomepage(
            @RequestParam(defaultValue = "0") int page,
            Authentication authentication,
            Model model
    ) {
        model.addAttribute("active", "homepage");

        Long currentUserId = resolveCurrentUserId(authentication);
        // Nếu chưa đăng nhập, vẫn cho xem trang chủ nhưng không có thông tin cá nhân
        // (Hoặc return "redirect:/login" nếu bạn muốn bắt buộc đăng nhập)

        try {
            // 1) Xử lý thông tin khách hàng và lịch hẹn
            if (currentUserId != null) {
                CustomerProfile profile = profileService.getCurrentCustomerProfile(currentUserId);
                if (profile != null) {
                    model.addAttribute("customer", profile);
                    model.addAttribute("appointments", profileService.getCustomerAppointments(profile.getId()));
                } else {
                    setDefaultCustomerModel(model);
                }
            } else {
                setDefaultCustomerModel(model);
            }

            // 2) Load danh sách Dịch vụ và Bác sĩ (chỉ lấy những cái đang Active)
            model.addAttribute("services", serviceRepo.findByActiveTrue());
            model.addAttribute("dentists", dentistRepo.filterDentists(null, UserStatus.ACTIVE));

            // 3) Load danh sách Blogs (Phân trang và chỉ lấy APPROVED)
            int safePage = Math.max(page, 0);
            Pageable pageable = PageRequest.of(safePage, 2);

            // Lưu ý: Tên hàm repository phải khớp với file BlogRepository của bạn
            // Ở đây tôi dùng findByStatus khớp với nghiệp vụ APPROVED
            Page<Blog> blogPage = blogRepo.findByStatusOrderByApprovedAtDesc(BlogStatus.APPROVED, pageable);

            model.addAttribute("blogs", blogPage.getContent());
            model.addAttribute("currentPage", safePage);
            model.addAttribute("totalPages", blogPage.getTotalPages());

            return "customer/homepage";

        } catch (Exception e) {
            // Trường hợp lỗi hệ thống, trả về list rỗng để tránh crash giao diện Thymeleaf
            model.addAttribute("error", "Có lỗi xảy ra khi tải dữ liệu.");
            setDefaultCustomerModel(model);
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
        model.addAttribute("services", serviceRepo.findByActiveTrue());
        return "customer/booking";
    }

    @GetMapping("/customer/my-appointments")
    public String appointmentsPage(Model model) {
        model.addAttribute("active", "appointments");
        return "customer/appointments";
    }

    // Helper method để tránh lặp code
    private void setDefaultCustomerModel(Model model) {
        CustomerProfile guest = new CustomerProfile();
        guest.setFullName("Khách hàng");
        model.addAttribute("customer", guest);
        model.addAttribute("appointments", new ArrayList<>());
    }

    private Long resolveCurrentUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principal = authentication.getPrincipal();
        String email;

        if (principal instanceof UserDetails userDetails) {
            email = userDetails.getUsername();
        } else {
            email = authentication.getName();
        }

        return userRepository.findByEmail(email)
                .map(user -> user.getId())
                .orElse(null);
    }
}