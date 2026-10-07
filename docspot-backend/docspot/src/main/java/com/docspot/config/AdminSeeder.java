package com.docspot.config;

import com.docspot.entity.User;
import com.docspot.enums.Role;
import com.docspot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Runs once on every app startup.
 * Creates the admin account if it doesn't exist yet.
 * Admin credentials are read from application.properties — change them before deploying.
 *
 * Admin login:
 *   email    → app.admin.email    (default: admin@docspot.com)
 *   password → app.admin.password (default: Admin@123)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email}")
    private String adminEmail;

    @Value("${app.admin.password}")
    private String adminPassword;

    @Value("${app.admin.name}")
    private String adminName;

    @Override
    public void run(String... args) {
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .name(adminName)
                    .email(adminEmail)
                    .mobile("0000000000")   // placeholder — admin doesn't use mobile
                    .password(passwordEncoder.encode(adminPassword))
                    .role(Role.ADMIN)
                    .deleted(false)
                    .build();

            userRepository.save(admin);
            log.info("✅ Admin account seeded → email: {}", adminEmail);
        } else {
            log.info("ℹ️  Admin account already exists — skipping seed.");
        }
    }
}