package com.dentalclinic.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class WalletSchemaInitializer {

    @Bean
    public ApplicationRunner walletSchemaRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
        };
    }
}
