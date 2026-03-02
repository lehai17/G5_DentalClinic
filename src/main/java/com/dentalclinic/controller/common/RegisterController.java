package com.dentalclinic.controller.common;

import com.dentalclinic.dto.customer.RegisterRequest;
import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.user.Gender;
import com.dentalclinic.model.user.Role;
import com.dentalclinic.model.user.User;
import com.dentalclinic.model.user.UserStatus;
import com.dentalclinic.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/register")
    public String registerPage(Model model,
                               @RequestParam(value = "success", required = false) String success) {

        if (!model.containsAttribute("form")) {
            model.addAttribute( "form", new RegisterRequest());
        }
        model.addAttribute("success", success != null);
        return "customer/register";
    }

    @PostMapping("/register")
    public String handleRegister(@Valid @ModelAttribute("form") RegisterRequest form,
                                 BindingResult bindingResult,
                                 Model model) {

        // 1) lỗi validate cơ bản
        if (bindingResult.hasErrors()) {
            return "customer/register";
        }

        // 2) check trùng email
        if (userRepository.existsByEmail(form.getEmail())) {
            bindingResult.rejectValue("email", "duplicate", "Email đã tồn tại");
            return "customer/register";
        }

        // 3) check trùng phone
        if (userRepository.existsByCustomerProfile_Phone(form.getPhone())) {
            bindingResult.rejectValue("phone", "duplicate", "Số điện thoại đã tồn tại");
            return "customer/register";
        }

        // 4) save
        User user = new User();
        user.setEmail(form.getEmail());
        user.setPassword(passwordEncoder.encode(form.getPassword()));
        user.setRole(Role.CUSTOMER);
        user.setStatus(UserStatus.ACTIVE);
        user.setGender(Gender.valueOf(form.getGender().toUpperCase()));
        user.setDateOfBirth(LocalDate.parse(form.getDateOfBirth()));

        CustomerProfile profile = new CustomerProfile();
        profile.setFullName(form.getFullName());
        profile.setPhone(form.getPhone());
        profile.setAddress(null);

        user.setCustomerProfile(profile);
        userRepository.save(user);


        return "redirect:/login?registered=true";
    }
}
