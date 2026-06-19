package com.asa.asaunify.config;

import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.repos.UserRepo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public void run(ApplicationArguments args){
        seedAdmin();
    }

    private void seedAdmin(){

        String email = "ishimwelorie45@gmail.com";

        if (userRepository.findByEmail(email).isPresent()) {
            log.info("Admin user already exists — skipping seed");
            return;
        }


        User admin = User.builder()
                .fullName("System Admin")
                .email(email)
                .passwordHash(passwordEncoder.encode("Admin@1234"))
                .role(Role.ADMIN)
                .isActive(true)
                .build();

        userRepository.save(admin);

        log.info("========================================");
        log.info("Admin user created successfully");
        log.info("Email:    admin@asaunify.com");
        log.info("Password: Admin@1234");
        log.info("CHANGE THIS PASSWORD AFTER FIRST LOGIN");
        log.info("========================================");

    }

}
