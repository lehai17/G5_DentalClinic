package com.dentalclinic.config;

import com.dentalclinic.model.appointment.AppointmentStatus;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Arrays;
import java.util.stream.Collectors;

@Configuration
public class AppointmentStatusConstraintInitializer {

    private static final String CONSTRAINT_NAME = "CK_appointment_status";

    @Bean
    public ApplicationRunner appointmentStatusConstraintRunner(JdbcTemplate jdbcTemplate) {
        return args -> {
            Integer exists = jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM sys.check_constraints
                    WHERE name = ?
                    """,
                    Integer.class,
                    CONSTRAINT_NAME
            );

            if (exists != null && exists > 0) {
                jdbcTemplate.execute("ALTER TABLE appointment DROP CONSTRAINT " + CONSTRAINT_NAME);
            }

            String allowedStatuses = Arrays.stream(AppointmentStatus.values())
                    .map(Enum::name)
                    .map(status -> "'" + status + "'")
                    .collect(Collectors.joining(", "));

            jdbcTemplate.execute(
                    "ALTER TABLE appointment ADD CONSTRAINT " + CONSTRAINT_NAME
                            + " CHECK (status IN (" + allowedStatuses + "))"
            );
        };
    }
}
