package com.dentalclinic.controller.customer;

import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/customer/wallet")
//@RequiredArgsConstructor
public class CustomerWalletController {
    private static final String SESSION_USER_ID = "userId";

    private final WalletService walletService;
    private final UserRepository userRepository;

    public CustomerWalletController(WalletService walletService, UserRepository userRepository) {
        this.walletService = walletService;
        this.userRepository = userRepository;
    }

    private Long getCurrentUserId(HttpSession session) {
        Object uid = session.getAttribute(SESSION_USER_ID);
        Long userId = null;
        if (uid instanceof Long) userId = (Long) uid;
        else if (uid instanceof Number) userId = ((Number) uid).longValue();
        if (userId == null || !userRepository.existsById(userId)) return null;
        return userId;
    }

    @GetMapping
    public String walletPage(HttpSession session, Model model) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        Optional<Wallet> wallet = walletService.getWalletByUserId(userId);
        model.addAttribute("wallet", wallet.orElse(null));
        return "customer/wallet";
    }

    @GetMapping("/api")
    public ResponseEntity<?> getWalletData(HttpSession session) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
        }

        Optional<Wallet> walletOpt = walletService.getWalletByUserId(userId);
        if (walletOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "balance", 0.0,
                    "transactions", List.of()
            ));
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
}
