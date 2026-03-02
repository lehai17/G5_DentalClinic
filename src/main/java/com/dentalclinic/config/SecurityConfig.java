package com.dentalclinic.config;

import com.dentalclinic.repository.UserRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(
            HttpSecurity http,
            RoleBasedSuccessHandler successHandler,
            GoogleOAuth2SuccessHandler googleOAuth2SuccessHandler
    ) throws Exception {

        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/homepage",
                                "/login", "/register",
                                "/css/**", "/images/**", "/js/**",
                                "/oauth2/**",
                                "/login/oauth2/**","/forgot-password","/verify-code","/reset-password"
                        ).permitAll()

                        .requestMatchers("/staff/support/**").hasAnyRole("STAFF", "ADMIN")
                        .requestMatchers("/staff/**").hasRole("STAFF")
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/dentist/**").hasRole("DENTIST")
                        .requestMatchers("/support/**").hasRole("CUSTOMER")
                        .requestMatchers("/customer/**").hasRole("CUSTOMER")
                        .anyRequest().authenticated()
                )

                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/do-login")
                        .successHandler(successHandler)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )

                // ✅ BẬT GOOGLE LOGIN Ở ĐÂY
                .oauth2Login(oauth -> oauth
                        .loginPage("/login")
                        .successHandler(googleOAuth2SuccessHandler) //
                )

                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )

                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return NoOpPasswordEncoder.getInstance();
    }

    @Bean
    public UserDetailsService userDetailsService(UserRepository userRepository) {
        return username -> {
            com.dentalclinic.model.user.User user = userRepository
                    .findByEmail(username)
                    .orElseThrow(() -> new UsernameNotFoundException("User not found"));

            return org.springframework.security.core.userdetails.User
                    .withUsername(user.getEmail())
                    .password(user.getPassword())
                    .roles(user.getRole().name())
                    .build();
        };
    }
}
