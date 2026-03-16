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
            addColumnIfMissing(jdbcTemplate, "wallets", "pin_code", "NVARCHAR(255) NULL");
            addColumnIfMissing(jdbcTemplate, "wallets", "pin_failed_attempts", "INT NOT NULL CONSTRAINT DF_wallets_pin_failed_attempts DEFAULT 0");
            addColumnIfMissing(jdbcTemplate, "wallets", "pin_locked_until", "DATETIME2 NULL");
            addColumnIfMissing(jdbcTemplate, "wallets", "pin_reset_otp_hash", "NVARCHAR(255) NULL");
            addColumnIfMissing(jdbcTemplate, "wallets", "pin_reset_otp_expires_at", "DATETIME2 NULL");
            addColumnIfMissing(jdbcTemplate, "wallets", "pin_reset_verified_until", "DATETIME2 NULL");
        };
    }

    private void addColumnIfMissing(JdbcTemplate jdbcTemplate,
                                    String tableName,
                                    String columnName,
                                    String columnDefinition) {
        Integer exists = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """,
                Integer.class,
                tableName,
                columnName
        );

        if (exists != null && exists > 0) {
            return;
        }

        jdbcTemplate.execute(
                "ALTER TABLE " + tableName + " ADD " + columnName + " " + columnDefinition
        );
    }
}
