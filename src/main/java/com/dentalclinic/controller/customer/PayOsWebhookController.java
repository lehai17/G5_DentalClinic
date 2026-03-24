package com.dentalclinic.controller.customer;

import com.dentalclinic.service.staff.PayOsService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.view.RedirectView;

import java.util.Map;

@Controller
@RequestMapping("/public/payos")
public class PayOsWebhookController {

    private final PayOsService payOsService;

    public PayOsWebhookController(PayOsService payOsService) {
        this.payOsService = payOsService;
    }

    @PostMapping("/webhook")
    @ResponseBody
    public ResponseEntity<?> webhook(@RequestBody String payload) {
        try {
            payOsService.handleWebhook(payload);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (RuntimeException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", ex.getMessage() != null ? ex.getMessage() : "Webhook khong hop le."
            ));
        }
    }

    @GetMapping("/return")
    public RedirectView payosReturn() {
        return new RedirectView("/");
    }

    @GetMapping("/cancel")
    public RedirectView payosCancel() {
        return new RedirectView("/");
    }
}
