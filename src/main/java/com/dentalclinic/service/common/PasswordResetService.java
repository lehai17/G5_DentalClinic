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
        // Khong tiet lo email co ton tai hay khong (best practice)
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
                .orElseThrow(() -> new RuntimeException("Không tìm thấy yêu cầu đặt lại mật khẩu"));

        if (pr.isUsed()) throw new RuntimeException("Mã xác thực đã được sử dụng");
        if (pr.getExpiresAt().isBefore(LocalDateTime.now())) throw new RuntimeException("Mã xác thực đã hết hạn");

        // so sanh code nguoi dung nhap voi code_hash
        if (!passwordEncoder.matches(code, pr.getCodeHash())) {
            throw new RuntimeException("Mã xác thực không hợp lệ");
        }

        // mark used? (tuy ban). O day mark "used = true" de code chi dung 1 lan
        pr.setUsed(true);
        passwordResetRepository.save(pr);

        return pr.getToken(); // dung token de qua buoc reset password
    }

    public void resetPassword(String token, String newPassword) {
        PasswordReset pr = passwordResetRepository
                .findByToken(token)
                .orElseThrow(() -> new RuntimeException("Liên kết đặt lại mật khẩu không hợp lệ"));

        // token nay phai da verify code (used=true) va chua het han
        if (!pr.isUsed()) throw new RuntimeException("Mã xác thực chưa được xác minh");
        if (pr.getExpiresAt().isBefore(LocalDateTime.now())) throw new RuntimeException("Yêu cầu đặt lại mật khẩu đã hết hạn");

        User user = userRepository.findByEmail(pr.getEmail())
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // xoa reset request de sach DB
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
        msg.setSubject("GENZ CLINIC - M\u00e3 \u0111\u1eb7t l\u1ea1i m\u1eadt kh\u1ea9u");
        msg.setText("M\u00e3 x\u00e1c th\u1ef1c c\u1ee7a b\u1ea1n l\u00e0: " + code + "\nM\u00e3 c\u00f3 hi\u1ec7u l\u1ef1c trong 10 ph\u00fat.");
        mailSender.send(msg);
    }
}


