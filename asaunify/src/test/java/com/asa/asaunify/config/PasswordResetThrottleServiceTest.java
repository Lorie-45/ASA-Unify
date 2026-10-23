package com.asa.asaunify.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordResetThrottleServiceTest {

    private PasswordResetThrottleService service() {
        PasswordResetThrottleService s = new PasswordResetThrottleService();
        ReflectionTestUtils.setField(s, "windowMinutes", 15L);
        ReflectionTestUtils.setField(s, "maxAttemptsPerWindow", 3);
        return s;
    }

    @Test
    void attemptsAreCappedPerSourceWithoutACooldown() {
        PasswordResetThrottleService s = service();
        assertTrue(s.tryAttempt("ip:1.1.1.1"));
        assertTrue(s.tryAttempt("ip:1.1.1.1"));   // immediate retry allowed
        assertTrue(s.tryAttempt("ip:1.1.1.1"));
        assertFalse(s.tryAttempt("ip:1.1.1.1"));
        assertTrue(s.tryAttempt("ip:2.2.2.2"));   // other sources unaffected
    }
}
