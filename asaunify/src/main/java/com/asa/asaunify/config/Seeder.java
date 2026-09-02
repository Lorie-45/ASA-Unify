package com.asa.asaunify.config;

import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.repos.UserRepo;
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

    // Seed admin credentials come from the environment. The dev-only fallback
    // password is used solely for local bootstrapping; production MUST set
    // SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD to a strong, private value.
    @Value("${seed.admin.email:admin@asaunify.local}")
    private String adminEmail;

    @Value("${seed.admin.password:ChangeMe#Local1}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args){
        seedAdmin();
    }

    private void seedAdmin(){

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

        // Do NOT log the password. Log only the email actually used so the
        // operator knows which account was created.
        log.info("========================================");
        log.info("Seed admin created: {}", adminEmail);
        log.info("Set SEED_ADMIN_PASSWORD in the environment and change the");
        log.info("password after first login.");
        log.info("========================================");
    }

}
