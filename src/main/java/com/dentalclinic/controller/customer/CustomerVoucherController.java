package com.dentalclinic.controller.customer;

import com.dentalclinic.model.promotion.Voucher;
import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.customer.CustomerVoucherWalletService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/customer/vouchers")
public class CustomerVoucherController {

    private static final String SESSION_USER_ID = "userId";

    private final CustomerVoucherWalletService customerVoucherWalletService;
    private final UserRepository userRepository;

    public CustomerVoucherController(CustomerVoucherWalletService customerVoucherWalletService,
                                     UserRepository userRepository) {
        this.customerVoucherWalletService = customerVoucherWalletService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String voucherWalletPage(HttpSession session, Model model) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        List<Voucher> vouchers = customerVoucherWalletService.getAvailableVouchers(userId);
        model.addAttribute("active", "vouchers");
        model.addAttribute("vouchers", vouchers);
        model.addAttribute("voucherWalletCount", vouchers.size());
        model.addAttribute("voucherService", customerVoucherWalletService);
        return "customer/voucher-wallet";
    }

    private Long getCurrentUserId(HttpSession session) {
        Object uid = session.getAttribute(SESSION_USER_ID);
        Long userId = null;
        if (uid instanceof Long) {
            userId = (Long) uid;
        } else if (uid instanceof Number) {
            userId = ((Number) uid).longValue();
        }
        if (userId == null || !userRepository.existsById(userId)) {
            return null;
        }
        return userId;
    }
}
