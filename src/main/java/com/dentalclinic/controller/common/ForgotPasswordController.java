package com.dentalclinic.controller.common;

import com.dentalclinic.service.common.PasswordResetService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
public class ForgotPasswordController {

    private final PasswordResetService passwordResetService;

    public ForgotPasswordController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    @GetMapping("/forgot-password")
    public String forgotPage() {
        return "customer/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String handleForgot(@RequestParam String email) {
        passwordResetService.requestReset(email);
        // luôn redirect sang verify để không lộ email tồn tại hay không
        return "redirect:/verify-code?email=" + email;
    }

    @GetMapping("/verify-code")
    public String verifyPage() {
        return "customer/verify-code";
    }

    @PostMapping("/verify-code")
    public String handleVerify(@RequestParam String email,
                               @RequestParam String code) {
        try {
            String token = passwordResetService.verifyCode(email, code);
            return "redirect:/reset-password?token=" + token;
        } catch (Exception e) {
            return "redirect:/verify-code?email=" + email + "&error=true";
        }
    }

    @GetMapping("/reset-password")
    public String resetPage() {
        return "customer/reset-password";
    }

    @PostMapping("/reset-password")
    public String handleReset(@RequestParam String token,
                              @RequestParam String newPassword,
                              @RequestParam String confirmPassword) {
        if (!newPassword.equals(confirmPassword)) {
            return "redirect:/reset-password?token=" + token + "&mismatch=true";
        }

        try {
            passwordResetService.resetPassword(token, newPassword);
            return "redirect:/login?resetSuccess=true";
        } catch (Exception e) {
            return "redirect:/reset-password?token=" + token + "&error=true";
        }
    }
}
