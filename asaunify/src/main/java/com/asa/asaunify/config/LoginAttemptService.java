package com.asa.asaunify.config;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory brute-force guard for the login endpoint.
 *
 * After {@link #MAX_ATTEMPTS} consecutive failures for a given key
 * (email + client IP), further attempts are blocked for {@link #LOCK_MINUTES}
 * minutes. A successful login clears the counter.
 *
 * Note: state is per-instance and not shared across nodes or restarts. For a
 * multi-instance deployment back this with a shared store (e.g. Redis /
 * Bucket4j). It is a meaningful control for a single-instance service and has
 * no external dependencies.
 */
@Service
public class LoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCK_MINUTES = 15;

    private static final class Attempt {
        int count;
        Instant lockedUntil;
    }

    private final ConcurrentHashMap<String, Attempt> attempts =
            new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Attempt a = attempts.get(key);
        if (a == null || a.lockedUntil == null) {
            return false;
        }
        if (Instant.now().isAfter(a.lockedUntil)) {
            // Lock window elapsed — reset.
            attempts.remove(key);
            return false;
        }
        return true;
    }

    public void loginFailed(String key) {
        Attempt a = attempts.computeIfAbsent(key, k -> new Attempt());
        synchronized (a) {
            a.count++;
            if (a.count >= MAX_ATTEMPTS) {
                a.lockedUntil = Instant.now().plus(Duration.ofMinutes(LOCK_MINUTES));
            }
        }
    }

    public void loginSucceeded(String key) {
        attempts.remove(key);
    }

    public long getLockMinutes() {
        return LOCK_MINUTES;
    }
}
