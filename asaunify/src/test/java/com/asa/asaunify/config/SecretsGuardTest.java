package com.asa.asaunify.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecretsGuardTest {

    private static final String DEV_JWT =
            "6b621737d1c7c6ee7143a4198928e90c2ce7d77e158a0aa23c994875a035d083";

    private MockEnvironment env(String jwt, String db, String admin) {
        MockEnvironment e = new MockEnvironment()
                .withProperty("jwt.secret", jwt)
                .withProperty("spring.datasource.password", db);
        if (admin != null) e.withProperty("seed.admin.password", admin);
        return e;
    }

    @Test
    void devDefaultsOnlyWarnOutsideProduction() {
        assertDoesNotThrow(() -> new SecretsGuard(env(DEV_JWT, "1234", null)).check());
    }

    @Test
    void devDefaultsStopTheAppInProductionProfile() {
        MockEnvironment e = env(DEV_JWT, "1234", null);
        e.setActiveProfiles("prod");
        assertThrows(IllegalStateException.class, () -> new SecretsGuard(e).check());
    }

    @Test
    void strictFlagAlsoEnforces() {
        MockEnvironment e = env(DEV_JWT, "1234", null).withProperty("app.strict-secrets", "true");
        assertThrows(IllegalStateException.class, () -> new SecretsGuard(e).check());
    }

    @Test
    void realSecretsPassEvenInProduction() {
        MockEnvironment e = env("x".repeat(48), "a-real-password", "Str0ng&Private!");
        e.setActiveProfiles("prod");
        assertDoesNotThrow(() -> new SecretsGuard(e).check());
    }
}
