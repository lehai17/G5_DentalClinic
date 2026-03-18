package com.dentalclinic.controller.customer;

import com.dentalclinic.exception.BusinessException;
import com.dentalclinic.service.customer.CustomerPaymentHistoryService;
import com.dentalclinic.repository.UserRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/customer/payments")
public class CustomerPaymentHistoryController {

    private static final String SESSION_USER_ID = "userId";

    private final CustomerPaymentHistoryService customerPaymentHistoryService;
    private final UserRepository userRepository;

    public CustomerPaymentHistoryController(CustomerPaymentHistoryService customerPaymentHistoryService,
                                            UserRepository userRepository) {
        this.customerPaymentHistoryService = customerPaymentHistoryService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String paymentHistory(HttpSession session, Model model) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        model.addAttribute("active", "payments");
        model.addAttribute("paymentHistory", customerPaymentHistoryService.getPaymentHistory(userId));
        return "customer/payment-history";
    }

    @GetMapping("/deposit/{appointmentId}")
    public String depositReceipt(@PathVariable Long appointmentId, HttpSession session, Model model) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        model.addAttribute("active", "payments");
        model.addAttribute("receipt", customerPaymentHistoryService.getDepositReceipt(userId, appointmentId));
        return "customer/payment-receipt";
    }

    @GetMapping("/invoice/{invoiceId}")
    public String invoiceReceipt(@PathVariable Long invoiceId, HttpSession session, Model model) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        model.addAttribute("active", "payments");
        model.addAttribute("receipt", customerPaymentHistoryService.getInvoiceReceipt(userId, invoiceId));
        return "customer/payment-receipt";
    }

    @GetMapping("/wallet/{transactionId}")
    public String walletReceipt(@PathVariable Long transactionId, HttpSession session, Model model) {
        Long userId = getCurrentUserId(session);
        if (userId == null) {
            return "redirect:/login";
        }

        model.addAttribute("active", "payments");
        model.addAttribute("receipt", customerPaymentHistoryService.getWalletReceipt(userId, transactionId));
        return "customer/payment-receipt";
    }

    @ExceptionHandler({BusinessException.class, IllegalArgumentException.class})
    public String handleBusinessError(RuntimeException ex, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        return "redirect:/customer/payments";
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
