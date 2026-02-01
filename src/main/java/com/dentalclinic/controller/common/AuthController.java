package com.dentalclinic.controller.common;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AuthController {

    @GetMapping("/login")
    public String login() {
        return "/customer/login";
    }

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

}
