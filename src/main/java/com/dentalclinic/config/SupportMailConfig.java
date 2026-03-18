package com.dentalclinic.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

@Configuration
public class SupportMailConfig {

    @Bean(name = "supportMailSender")
    public JavaMailSender supportMailSender(
            @Value("${spring.mail.host:smtp.gmail.com}") String host,
            @Value("${spring.mail.port:587}") int port,
            @Value("${spring.mail.username:}") String springMailUsername,
            @Value("${spring.mail.password:}") String springMailPassword,
            @Value("${mail.support.username:}") String supportUsername,
            @Value("${mail.support.password:}") String supportPassword,
            @Value("${spring.mail.properties.mail.smtp.auth:true}") String smtpAuth,
            @Value("${spring.mail.properties.mail.smtp.starttls.enable:true}") String startTlsEnabled,
            @Value("${spring.mail.properties.mail.smtp.connectiontimeout:5000}") String connectionTimeout,
            @Value("${spring.mail.properties.mail.smtp.timeout:5000}") String timeout,
            @Value("${spring.mail.properties.mail.smtp.writetimeout:5000}") String writeTimeout
    ) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());

        String username = StringUtils.hasText(supportUsername) ? supportUsername : springMailUsername;
        String password = StringUtils.hasText(supportPassword) ? supportPassword : springMailPassword;

        sender.setUsername(username);
        sender.setPassword(password);

        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.smtp.auth", smtpAuth);
        properties.put("mail.smtp.starttls.enable", startTlsEnabled);
        properties.put("mail.smtp.connectiontimeout", connectionTimeout);
        properties.put("mail.smtp.timeout", timeout);
        properties.put("mail.smtp.writetimeout", writeTimeout);
        properties.put("mail.mime.charset", StandardCharsets.UTF_8.name());
        return sender;
    }
}
