package com.dentalclinic.controller.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "/customer/login"; // -> templates/login.html
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }
}
