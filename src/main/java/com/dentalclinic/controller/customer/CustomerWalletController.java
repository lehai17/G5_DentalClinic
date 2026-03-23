package com.dentalclinic.controller.customer;

import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.model.wallet.WalletTransactionType;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.repository.WalletTransactionRepository;
import com.dentalclinic.service.wallet.WalletService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/customer/wallet")
public class CustomerWalletController {
    private static final String SESSION_USER_ID = "userId";

    private final WalletService walletService;
    private final UserRepository userRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    public CustomerWalletController(WalletService walletService,
                                    UserRepository userRepository,
                                    WalletTransactionRepository walletTransactionRepository) {
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.walletTransactionRepository = walletTransactionRepository;
    }

    private Long getCurrentUserId(HttpSession session) {
        Object uid = session.getAttribute(SESSION_USER_ID);
        Long userId = null;
        if (uid instanceof Long) {
            userId = (Long) uid;
        } else if (uid instanceof Number) {
            userId = ((Number) uid).longValue();
        }
        if (userId != null && userRepository.existsById(userId)) {
            return userId;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return null;
        }

        return userRepository.findByEmail(authentication.getName())
                .map(user -> {
                    session.setAttribute(SESSION_USER_ID, user.getId());
                    return user.getId();
                })
                .orElse(null);
    }

    @GetMapping
    public String walletPage(HttpSession session,
                             Model model,
                             @RequestParam(required = false) String topup,
                             @RequestParam(required = false) String txnRef) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        Optional<Wallet> wallet = walletService.getWalletByUserId(userId);
        model.addAttribute("wallet", wallet.orElse(null));
        applyTopupStatusMessage(model, userId, topup, txnRef);
        return "customer/wallet";
    }

    @GetMapping("/api")
    public ResponseEntity<?> getWalletData(HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Chưa đăng nhập."));
        }

        Optional<Wallet> walletOpt = walletService.getWalletByUserId(userId);
        if (walletOpt.isEmpty()) {
            Map<String, Object> emptyResponse = new HashMap<>();
            emptyResponse.put("balance", 0.0);
            emptyResponse.put("transactions", List.of());
            return ResponseEntity.ok(emptyResponse);
        }

        Wallet wallet = walletOpt.get();
        List<WalletTransaction> transactions = walletService.getTransactionHistory(wallet);

        Map<String, Object> response = new HashMap<>();
        response.put("balance", wallet.getBalance().doubleValue());
        response.put("transactions", transactions.stream().map(t -> Map.of(
                "id", t.getId(),
                "type", t.getType().toString(),
                "amount", t.getAmount().doubleValue(),
                "description", t.getDescription() != null ? t.getDescription() : "",
                "createdAt", t.getCreatedAt().toString(),
                "status", t.getStatus().toString()
        )).toList());

        return ResponseEntity.ok(response);
    }

    private void applyTopupStatusMessage(Model model, Long userId, String topup, String txnRef) {
        if (topup == null || topup.isBlank()) {
            return;
        }

        String normalizedStatus = topup.trim().toLowerCase();
        String normalizedTxnRef = txnRef == null ? "" : txnRef.trim();
        boolean hasTransactionReference = !normalizedTxnRef.isBlank();
        boolean verifiedSuccess = hasTransactionReference && walletTransactionRepository
                .existsByWallet_Customer_User_IdAndTypeAndDescription(
                        userId,
                        WalletTransactionType.DEPOSIT,
                        "Nap tien vi qua VNPay [" + normalizedTxnRef + "]"
                );

        if ("success".equals(normalizedStatus) && verifiedSuccess) {
            model.addAttribute("walletStatusType", "success");
            model.addAttribute("walletStatusTitle", "Nạp tiền thành công");
            model.addAttribute("walletStatusMessage", "Nạp tiền vào ví thành công. Ví nhận đủ 100% giá trị giao dịch.");
            return;
        }

        if ("fail".equals(normalizedStatus) && hasTransactionReference) {
            model.addAttribute("walletStatusType", "warning");
            model.addAttribute("walletStatusTitle", "Nạp tiền chưa hoàn tất");
            model.addAttribute("walletStatusMessage", "Giao dịch nạp tiền chưa hoàn tất hoặc không làm thay đổi số dư ví.");
            return;
        }

        model.addAttribute("walletStatusType", "info");
        model.addAttribute("walletStatusTitle", "Không ghi nhận giao dịch mới");
        model.addAttribute("walletStatusMessage", "Không ghi nhận thay đổi số dư cho liên kết này. Có thể bạn đã mở lại liên kết cũ hoặc giao dịch không hợp lệ.");
    }
}
