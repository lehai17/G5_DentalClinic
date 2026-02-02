package com.dentalclinic.service.common;

import com.dentalclinic.model.user.PasswordReset;
import com.dentalclinic.model.user.User;
import com.dentalclinic.repository.PasswordResetRepository;
import com.dentalclinic.repository.UserRepository;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetRepository passwordResetRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository userRepository,
                                PasswordResetRepository passwordResetRepository,
                                JavaMailSender mailSender,
                                PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordResetRepository = passwordResetRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    public void requestReset(String email) {
        // Không tiết lộ email có tồn tại hay không (best practice)
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) return;

        String code = generate6DigitCode();
        String token = generateToken();

        PasswordReset pr = new PasswordReset();
        pr.setEmail(email);
        pr.setCodeHash(passwordEncoder.encode(code)); // hash code
        pr.setToken(token);
        pr.setExpiresAt(LocalDateTime.now().plusMinutes(10));
        pr.setUsed(false);

        passwordResetRepository.save(pr);

        sendEmail(email, code);
    }

    public String verifyCode(String email, String code) {
        PasswordReset pr = passwordResetRepository
                .findTopByEmailOrderByCreatedAtDesc(email)
                .orElseThrow(() -> new RuntimeException("No reset request"));

        if (pr.isUsed()) throw new RuntimeException("Code already used");
        if (pr.getExpiresAt().isBefore(LocalDateTime.now())) throw new RuntimeException("Code expired");

        // so sánh code người dùng nhập với code_hash
        if (!passwordEncoder.matches(code, pr.getCodeHash())) {
            throw new RuntimeException("Invalid code");
        }

        // mark used? (tuỳ bạn). Ở đây mark "used = true" để code chỉ dùng 1 lần
        pr.setUsed(true);
        passwordResetRepository.save(pr);

        return pr.getToken(); // dùng token để qua bước reset password
    }

    public void resetPassword(String token, String newPassword) {
        PasswordReset pr = passwordResetRepository
                .findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid token"));

        // token này phải đã verify code (used=true) và chưa hết hạn
        if (!pr.isUsed()) throw new RuntimeException("Not verified");
        if (pr.getExpiresAt().isBefore(LocalDateTime.now())) throw new RuntimeException("Expired");

        User user = userRepository.findByEmail(pr.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // xóa reset request để sạch DB
        passwordResetRepository.delete(pr);
    }

    private String generate6DigitCode() {
        int n = random.nextInt(900000) + 100000;
        return String.valueOf(n);
    }

    private String generateToken() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes); // 32 chars hex
    }

    private void sendEmail(String to, String code) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject("GENZ CLINIC - Password Reset Code");
        msg.setText("Mã xác thực của bạn là: " + code + "\nMã có hiệu lực trong 10 phút.");
        mailSender.send(msg);
    }
}
