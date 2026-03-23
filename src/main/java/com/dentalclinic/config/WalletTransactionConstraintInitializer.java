package com.dentalclinic.config;

import com.dentalclinic.model.wallet.WalletTransactionType;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class WalletTransactionConstraintInitializer {

    private static final String TABLE_NAME = "wallet_transactions";
    private static final String TYPE_CONSTRAINT_NAME = "CK_wallet_transactions_type";

    @Bean
    public ApplicationRunner walletTransactionConstraintRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            jdbcTemplate.update("DELETE FROM " + TABLE_NAME + " WHERE type = 'WITHDRAW'");

            List<String> existingConstraints = jdbcTemplate.queryForList(
                    """
                    SELECT cc.name
                    FROM sys.check_constraints cc
                    INNER JOIN sys.tables t ON cc.parent_object_id = t.object_id
                    WHERE t.name = ?
                      AND cc.definition LIKE '%[type]%'
                    """,
                    String.class,
                    TABLE_NAME
            );

            for (String constraintName : existingConstraints) {
                jdbcTemplate.execute("ALTER TABLE " + TABLE_NAME + " DROP CONSTRAINT " + constraintName);
            }

            String allowedTypes = Arrays.stream(WalletTransactionType.values())
                    .map(Enum::name)
                    .map(type -> "'" + type + "'")
                    .collect(Collectors.joining(", "));

            jdbcTemplate.execute(
                    "ALTER TABLE " + TABLE_NAME
                            + " ADD CONSTRAINT " + TYPE_CONSTRAINT_NAME
                            + " CHECK (type IN (" + allowedTypes + "))"
            );
        };
    }
}
