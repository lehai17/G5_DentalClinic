package com.dentalclinic.config;

import com.dentalclinic.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collection;

@Component
public class RoleBasedSuccessHandler implements AuthenticationSuccessHandler {

    private static final String SESSION_USER_ID = "userId";

    private final UserRepository userRepository;

    public RoleBasedSuccessHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        Collection<? extends GrantedAuthority> authorities =
                authentication.getAuthorities();

        String redirectUrl = "/homepage";

        for (GrantedAuthority authority : authorities) {
            String role = authority.getAuthority();

            switch (role) {
                case "ROLE_ADMIN":
                    redirectUrl = "/admin/dashboard";
                    break;
                case "ROLE_STAFF":
                    redirectUrl = "/staff/dashboard";
                    break;
                case "ROLE_DENTIST":
                    redirectUrl = "/dentist/work-schedule";
                    break;
                case "ROLE_CUSTOMER":
                    redirectUrl = "/customer/homepage";
                    userRepository.findByEmail(authentication.getName())
                            .ifPresent(user -> request.getSession().setAttribute(SESSION_USER_ID, user.getId()));
                    break;
            }
        }

        response.sendRedirect(redirectUrl);
    }
}
