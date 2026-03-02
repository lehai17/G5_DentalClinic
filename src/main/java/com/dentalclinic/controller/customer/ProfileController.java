package com.dentalclinic.controller.customer;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
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

    // =========================
    // DTO for Edit Profile Form
    // =========================
    public static class EditProfileForm {

        @NotBlank(message = "Họ tên không được để trống")
        @Pattern(
                regexp = "^[A-Za-zÀ-ỹ\\s]+$",
                message = "Họ tên chỉ được chứa chữ cái và khoảng trắng"
        )
        private String fullName;

        // cho phép trống (user chưa muốn cập nhật), nhưng nếu nhập thì phải đúng định dạng
        @Pattern(
                regexp = "^$|^0\\d{8,9}$",
                message = "Số điện thoại phải bắt đầu bằng 0 và có 9–10 chữ số"
        )
        private String phone;

        private String address;

        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        private LocalDate dateOfBirth;

        public String getFullName() { return fullName; }
        public void setFullName(String fullName) { this.fullName = fullName; }

        public String getPhone() { return phone; }
        public void setPhone(String phone) { this.phone = phone; }

        public String getAddress() { return address; }
        public void setAddress(String address) { this.address = address; }

        public LocalDate getDateOfBirth() { return dateOfBirth; }
        public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
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

    //  mở trang edit + lấy dữ liệu vào form
    @GetMapping("/profile/edit")
    public String editProfilePage(Model model, Authentication authentication) {
        String email = extractEmail(authentication);
        if (email == null) return "redirect:/login";

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/login";

        CustomerProfile profile = profileService.getCurrentCustomerProfile(user.getId());

        // tạo form và fill dữ liệu hiện tại
        EditProfileForm form = new EditProfileForm();
        form.setDateOfBirth(user.getDateOfBirth());

        if (profile != null) {
            form.setFullName(profile.getFullName() == null ? "" : profile.getFullName());
            form.setPhone(profile.getPhone() == null ? "" : profile.getPhone());
            form.setAddress(profile.getAddress() == null ? "" : profile.getAddress());
        } else {
            form.setFullName("");
            form.setPhone("");
            form.setAddress("");
        }

        model.addAttribute("user", user);
        model.addAttribute("profile", profile);
        model.addAttribute("form", form);

        return "customer/edit-profile";
    }

    // lưu edit + validate
    @PostMapping("/profile/edit")
    public String handleEditProfile(
            @Valid @ModelAttribute("form") EditProfileForm form,
            BindingResult bindingResult,
            Authentication authentication,
            Model model
    ) {
        String email = extractEmail(authentication);
        if (email == null) return "redirect:/login";

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) return "redirect:/login";

        // nếu form lỗi -> trả lại trang + show lỗi field
        if (bindingResult.hasErrors()) {
            CustomerProfile profile = profileService.getCurrentCustomerProfile(user.getId());
            model.addAttribute("user", user);
            model.addAttribute("profile", profile);
            return "customer/edit-profile";
        }

        try {
            // 1) Update user
            user.setDateOfBirth(form.getDateOfBirth());
            userRepository.save(user);

            // 2) Update profile + tạo mới khi chưa có trường đó
            CustomerProfile profile = profileService.getCurrentCustomerProfile(user.getId());
            if (profile == null) {
                profile = new CustomerProfile();
                profile.setUser(user);
            }

            profile.setFullName(form.getFullName().trim());

            String phone = (form.getPhone() == null) ? "" : form.getPhone().trim();
            profile.setPhone(phone.isBlank() ? null : phone);

            String address = (form.getAddress() == null) ? "" : form.getAddress().trim();
            profile.setAddress(address.isBlank() ? null : address);

            customerProfileRepository.save(profile);

            model.addAttribute("success", "Cập nhật thông tin thành công!");

        } catch (Exception ex) {
            model.addAttribute("error", "Có lỗi khi cập nhật, vui lòng thử lại.");
        }

        // load lại trang
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
