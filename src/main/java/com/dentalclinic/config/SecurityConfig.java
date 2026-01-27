package com.dentalclinic.config;

import org.apache.catalina.User;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/login", "/css/**", "/images/**", "/js/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/do-login") // form POST /login
                        .defaultSuccessUrl("/dashboard", true)
                        .failureUrl("/login?error=true")
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout=true")
                        .permitAll()
                )
                // nếu bạn đang test form đơn giản, có thể bật tạm (chỉ khi cần)
                .csrf(csrf -> csrf.disable());

        return http.build();
    }

    // Tài khoản test (sau này bạn đổi sang DB)
    @Bean
    public UserDetailsService users() {
        UserDetails user = org.springframework.security.core.userdetails.User
                .withUsername("admin")
                .password("{noop}123456")
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(user);
    }

}
