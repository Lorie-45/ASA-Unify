package com.asa.asaunify.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Catches the development fallbacks in application.properties being used for
 * real. Each of them is a published value, so running with any of them means
 * anyone who has read the repository can forge tokens, open the database or log
 * in as the seeded admin.
 *
 * Outside production this only warns. With the "prod" profile active, or
 * app.strict-secrets=true, the application refuses to start.
 */
@Component
@Slf4j
public class SecretsGuard {

    private static final String DEV_JWT_SECRET =
            "6b621737d1c7c6ee7143a4198928e90c2ce7d77e158a0aa23c994875a035d083";
    private static final String DEV_DB_PASSWORD = "1234";
    private static final String DEV_ADMIN_PASSWORD = "ChangeMe#Local1";

    private final Environment env;

    public SecretsGuard(Environment env) {
        this.env = env;
    }

    @PostConstruct
    void check() {
        List<String> problems = new ArrayList<>();

        String jwt = env.getProperty("jwt.secret", "");
        if (DEV_JWT_SECRET.equals(jwt)) {
            problems.add("JWT_SECRET is the published development key");
        } else if (jwt.length() < 32) {
            problems.add("JWT_SECRET is shorter than 32 characters");
        }

        if (DEV_DB_PASSWORD.equals(env.getProperty("spring.datasource.password"))) {
            problems.add("DB_PASSWORD is the development default");
        }

        if (DEV_ADMIN_PASSWORD.equals(env.getProperty("seed.admin.password"))) {
            problems.add("seed.admin.password is the published development default");
        }

        if (problems.isEmpty()) {
            return;
        }

        boolean strict = Set.of(env.getActiveProfiles()).contains("prod")
                || env.getProperty("app.strict-secrets", Boolean.class, false);

        if (strict) {
            throw new IllegalStateException(
                    "Refusing to start with development secrets: "
                            + String.join("; ", problems));
        }
        problems.forEach(p -> log.warn("INSECURE CONFIG: {}", p));
    }
}
