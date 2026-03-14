package com.dentalclinic.controller.customer;

import com.dentalclinic.model.profile.CustomerProfile;
import com.dentalclinic.model.wallet.DemoBankAccount;
import com.dentalclinic.model.wallet.Wallet;
import com.dentalclinic.model.wallet.WalletTransaction;
import com.dentalclinic.repository.CustomerProfileRepository;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.wallet.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;

import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
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
    private final CustomerProfileRepository customerProfileRepository;

    public CustomerWalletController(WalletService walletService,
                                    UserRepository userRepository,
                                    CustomerProfileRepository customerProfileRepository) {
        this.walletService = walletService;
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
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
        CustomerProfile customer = customerProfileRepository.findByUser_Id(userId).orElse(null);
        DemoBankAccount savedWithdrawAccount = customer == null ? null : walletService.getPersonalWithdrawAccount(customer);
        if (walletOpt.isEmpty()) {
            return ResponseEntity.ok(Map.of(
                    "balance", 0.0,
                    "transactions", List.of(),
                    "savedWithdrawAccount", savedWithdrawAccount == null ? null : Map.of(
                            "bankName", savedWithdrawAccount.getBankName(),
                            "bankAccountNo", savedWithdrawAccount.getAccountNo(),
                            "accountHolder", savedWithdrawAccount.getAccountHolder(),
                            "balance", savedWithdrawAccount.getBalance().doubleValue()
                    )
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
        response.put("savedWithdrawAccount", savedWithdrawAccount == null ? null : Map.of(
                "bankName", savedWithdrawAccount.getBankName(),
                "bankAccountNo", savedWithdrawAccount.getAccountNo(),
                "accountHolder", savedWithdrawAccount.getAccountHolder(),
                "balance", savedWithdrawAccount.getBalance().doubleValue()
        ));

        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/withdraw")
    public ResponseEntity<?> withdraw(HttpSession session,
                                      @RequestParam BigDecimal amount,
                                      @RequestParam String mode,
                                      @RequestParam(required = false) String bankName,
                                      @RequestParam(required = false) String bankAccountNo) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Not authenticated"
            ));
        }

        if (amount == null || amount.compareTo(BigDecimal.valueOf(10_000L)) < 0) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "So tien rut toi thieu la 10.000 VND."
            ));
        }

        try {
            CustomerProfile customer = customerProfileRepository.findByUser_Id(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Khong tim thay thong tin khach hang."));
            DemoBankAccount targetAccount;
            if ("personal".equalsIgnoreCase(mode)) {
                if (bankName != null && !bankName.isBlank() && bankAccountNo != null && !bankAccountNo.isBlank()) {
                    targetAccount = walletService.savePersonalWithdrawAccount(customer, bankName, bankAccountNo);
                } else {
                    targetAccount = walletService.getPersonalWithdrawAccount(customer);
                    if (targetAccount == null) {
                        throw new IllegalArgumentException("Ban chua luu tai khoan ca nhan. Vui long nhap ngan hang va so tai khoan.");
                    }
                }
            } else if ("manual".equalsIgnoreCase(mode)) {
                if (bankName == null || bankName.isBlank() || bankAccountNo == null || !bankAccountNo.matches("\\d{10}")) {
                    throw new IllegalArgumentException("Vui long chon ngan hang va nhap so tai khoan gom 10 chu so.");
                }
                targetAccount = walletService.resolveDemoBankAccount(bankName, bankAccountNo);
            } else {
                throw new IllegalArgumentException("Che do rut tien khong hop le.");
            }

            WalletService.WithdrawResult result = walletService.withdraw(
                    customer,
                    amount,
                    targetAccount.getBankName(),
                    targetAccount.getAccountNo()
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rut tien demo thanh cong. Tien da chuyen vao tai khoan demo dich.",
                    "balance", result.getWalletBalance().doubleValue(),
                    "destinationBank", result.getDestinationAccount().getBankName(),
                    "destinationAccountNo", result.getDestinationAccount().getAccountNo(),
                    "destinationAccountHolder", result.getDestinationAccount().getAccountHolder(),
                    "destinationBalance", result.getDestinationAccount().getBalance().doubleValue()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Loi he thong khi rut tien demo: "
                            + ex.getClass().getSimpleName()
                            + (ex.getMessage() != null && !ex.getMessage().isBlank() ? " - " + ex.getMessage() : "")
            ));
        }
    }

    @PostMapping("/api/withdraw/personal-account")
    public ResponseEntity<?> savePersonalWithdrawAccount(HttpSession session,
                                                         @RequestParam String bankName,
                                                         @RequestParam String bankAccountNo) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Not authenticated"
            ));
        }

        try {
            CustomerProfile customer = customerProfileRepository.findByUser_Id(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Khong tim thay thong tin khach hang."));
            DemoBankAccount account = walletService.savePersonalWithdrawAccount(customer, bankName, bankAccountNo);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "bankName", account.getBankName(),
                    "bankAccountNo", account.getAccountNo(),
                    "accountHolder", account.getAccountHolder(),
                    "balance", account.getBalance().doubleValue()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Loi he thong khi luu tai khoan ca nhan: "
                            + ex.getClass().getSimpleName()
                            + (ex.getMessage() != null && !ex.getMessage().isBlank() ? " - " + ex.getMessage() : "")
            ));
        }
    }

    @GetMapping("/api/withdraw/account-lookup")
    public ResponseEntity<?> lookupWithdrawAccount(HttpSession session,
                                                   @RequestParam String bankName,
                                                   @RequestParam String bankAccountNo) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                    "success", false,
                    "message", "Not authenticated"
            ));
        }

        try {
            var account = walletService.resolveDemoBankAccount(bankName, bankAccountNo);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "bankName", account.getBankName(),
                    "bankAccountNo", account.getAccountNo(),
                    "accountHolder", account.getAccountHolder(),
                    "balance", account.getBalance().doubleValue()
            ));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage()
            ));
        } catch (Exception ex) {
            ex.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Loi he thong khi tra cuu tai khoan demo: "
                            + ex.getClass().getSimpleName()
                            + (ex.getMessage() != null && !ex.getMessage().isBlank() ? " - " + ex.getMessage() : "")
            ));
        }
    }
}
