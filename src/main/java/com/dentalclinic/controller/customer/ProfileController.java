package com.dentalclinic.controller.customer;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Optional;

@Controller
public class ProfileController {

    private final UserRepository userRepository;
    private final CustomerProfileService profileService;
    private final CustomerProfileRepository customerProfileRepository;

    public ProfileController(UserRepository userRepository,
                             CustomerProfileService profileService,
                             CustomerProfileRepository customerProfileRepository) {
        this.userRepository = userRepository;
        this.profileService = profileService;
        this.customerProfileRepository = customerProfileRepository;
    }

    @GetMapping("/profile")
    public String profile(Model model, Authentication authentication) {
        String email = extractEmail(authentication);
        if (email == null) return "redirect:/login";

        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return "redirect:/login";

        User user = userOpt.get();
        CustomerProfile profile = profileService.getCurrentCustomerProfile(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("profile", profile);

        return "customer/profile";
    }

    // ✅ GET: mở trang edit
    @GetMapping("/profile/edit")
    public String editProfilePage(Model model, Authentication authentication) {
        String email = extractEmail(authentication);
        if (email == null) return "redirect:/login";

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/login";

        CustomerProfile profile = profileService.getCurrentCustomerProfile(user.getId());

        model.addAttribute("user", user);
        model.addAttribute("profile", profile);

        return "customer/edit-profile";
    }

    // ✅ POST: lưu edit
    @PostMapping("/profile/edit")
    public String handleEditProfile(
            @RequestParam String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String address,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            Authentication authentication,
            Model model
    ) {
        String email = extractEmail(authentication);
        if (email == null) return "redirect:/login";

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/login";

        try {
            // 1) Update user
            user.setDateOfBirth(dateOfBirth);
            userRepository.save(user);

            // 2) Update customer profile (nếu chưa có thì tạo mới)
            CustomerProfile profile = profileService.getCurrentCustomerProfile(user.getId());
            if (profile == null) {
                profile = new CustomerProfile();
                profile.setUser(user);
            }

            profile.setFullName(fullName);
            profile.setPhone((phone == null || phone.isBlank()) ? null : phone.trim());
            profile.setAddress((address == null || address.isBlank()) ? null : address.trim());

            customerProfileRepository.save(profile);

            model.addAttribute("success", "Cập nhật thông tin thành công!");
        } catch (Exception ex) {
            model.addAttribute("error", "Có lỗi khi cập nhật, vui lòng thử lại.");
        }

        // load lại để render đúng
        CustomerProfile refreshed = profileService.getCurrentCustomerProfile(user.getId());
        model.addAttribute("user", user);
        model.addAttribute("profile", refreshed);

        return "customer/edit-profile";
    }

    private String extractEmail(Authentication auth) {
        if (auth == null) return null;
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails ud) return ud.getUsername();
        if (principal instanceof OAuth2User ou) return ou.getAttribute("email");
        return null;
    }
}
