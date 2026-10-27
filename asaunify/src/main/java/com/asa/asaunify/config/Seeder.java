package com.asa.asaunify.config;

import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.repos.UserRepo;
import com.asa.asaunify.util.EmailNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class Seeder implements ApplicationRunner {

    private final UserRepo userRepository;
    private final PasswordEncoder passwordEncoder;


    // Required, no fallback: startup fails if either is missing from application.properties.
    @Value("${seed.admin.email}")
    private String adminEmail;

    @Value("${seed.admin.password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args){
        seedAdmin();
    }

    private void seedAdmin(){

        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException(
                    "seed.admin.email and seed.admin.password must be set in application.properties");
        }

        // Stored in canonical form like every other address.
        adminEmail = EmailNormalizer.normalize(adminEmail);

        if (userRepository.findByEmail(adminEmail).isPresent()) {
            log.info("Admin user already exists — skipping seed");
            return;
        }

        User admin = User.builder()
                .fullName("System Admin")
                .email(adminEmail)
                .passwordHash(passwordEncoder.encode(adminPassword))
                .role(Role.ADMIN)
                .isActive(true)
                .build();

        userRepository.save(admin);


        log.info("========================================");
        log.info("Seed admin created: {}", adminEmail);
        log.info("Change the password after first login.");
        log.info("========================================");
    }

}
