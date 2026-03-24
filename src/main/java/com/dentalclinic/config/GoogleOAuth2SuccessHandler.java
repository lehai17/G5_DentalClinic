package com.dentalclinic.config;

import com.dentalclinic.repository.UserRepository;
import com.dentalclinic.service.common.OAuthUserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class GoogleOAuth2SuccessHandler implements AuthenticationSuccessHandler {
    private static final String SESSION_USER_ID = "userId";

    private final OAuthUserService oAuthUserService;
    private final UserRepository userRepository;

    public GoogleOAuth2SuccessHandler(OAuthUserService oAuthUserService,
                                      UserRepository userRepository) {
        this.oAuthUserService = oAuthUserService;
        this.userRepository = userRepository;
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {

        OAuth2User oauthUser = (OAuth2User) authentication.getPrincipal();

        oAuthUserService.upsertGoogleUser(oauthUser);

        String email = oauthUser.getAttribute("email");
        if (email != null && !email.isBlank()) {
            userRepository.findByEmail(email)
                    .ifPresent(user -> request.getSession().setAttribute(SESSION_USER_ID, user.getId()));
        }

        response.sendRedirect("/homepage");
    }
}