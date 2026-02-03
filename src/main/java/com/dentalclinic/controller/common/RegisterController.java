package com.dentalclinic.controller.common;

import com.dentalclinic.model.user.Gender;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import com.dentalclinic.model.profile.CustomerProfile;

import java.time.LocalDate;

@Controller
public class RegisterController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public RegisterController(UserRepository userRepository,
                              PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Hiện form
    @GetMapping("/register")
    public String registerPage() {
        return "customer/register";
    }

    // Xử lý submit
    @PostMapping("/register")
    public String handleRegister(
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String gender,
            @RequestParam String dateOfBirth,
            @RequestParam String fullName,
            @RequestParam String phone
    ) {
        // Check email tồn tại
        if (userRepository.findByEmail(email).isPresent()) {
            return "redirect:/register?error=true";
        }

        // ========== USER ==========
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setGender(Gender.valueOf(gender.toUpperCase()));
        user.setDateOfBirth(LocalDate.parse(dateOfBirth));

        // ========== CUSTOMER PROFILE ==========
        CustomerProfile profile = new CustomerProfile();
        profile.setFullName(fullName);
        profile.setPhone(phone);
        profile.setAddress(null); // có thể để trống cho user update sau

        // Gắn 2 chiều
        user.setCustomerProfile(profile);

        // Save 1 lần -> lưu cả user + profile
        userRepository.save(user);

        return "redirect:/login?registered=true";
    }

}
