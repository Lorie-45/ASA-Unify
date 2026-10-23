package com.asa.asaunify.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abuse guard for the forgot-password endpoint, following the same in-memory
 * approach as {@link LoginAttemptService}.
 *
 * Two limits apply per key: a short cooldown between consecutive requests, and
 * a cap on the number of requests inside a sliding window. Callers throttle on
 * both the submitted email and the client IP, so neither a single account nor
 * a single host can be used to flood the mail path.
 *
 * Crucially the caller applies this BEFORE looking the account up, and the
 * response is identical either way — a throttled request must not become a
 * signal that an account exists.
 *
 * Note: state is per-instance and lost on restart, exactly like the existing
 * login guard. The per-token attempt counter that actually protects a code
 * from brute force lives in the database instead, so a restart cannot reset
 * it. For a multi-instance deployment back this with a shared store.
 */
@Service
public class PasswordResetThrottleService {

    @Value("${app.password-reset.max-requests-per-window:3}")
    private int maxRequestsPerWindow;

    @Value("${app.password-reset.request-window-minutes:15}")
    private long windowMinutes;

    @Value("${app.password-reset.cooldown-seconds:60}")
    private long cooldownSeconds;

    private final ConcurrentHashMap<String, Deque<Instant>> requests =
            new ConcurrentHashMap<>();

    /**
     * Records a request against the key and reports whether it is allowed.
     * Returns false if the caller is inside the cooldown or has exhausted the
     * window allowance.
     */
    public boolean tryAcquire(String key) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(Duration.ofMinutes(windowMinutes));

        Deque<Instant> timestamps =
                requests.computeIfAbsent(key, k -> new ArrayDeque<>());

        synchronized (timestamps) {
            // Drop anything that has aged out of the window.
            while (!timestamps.isEmpty()
                    && timestamps.peekFirst().isBefore(windowStart)) {
                timestamps.pollFirst();
            }

            Instant last = timestamps.peekLast();
            if (last != null
                    && last.isAfter(now.minus(Duration.ofSeconds(cooldownSeconds)))) {
                return false;
            }

            if (timestamps.size() >= maxRequestsPerWindow) {
                return false;
            }

            timestamps.addLast(now);
            return true;
        }
    }

    /** Clears a key's history — used once a reset completes successfully. */
    public void reset(String key) {
        requests.remove(key);
    }

    /**
     * Drops keys whose entries have all aged out. Called opportunistically so
     * the map cannot grow without bound on a long-running instance.
     */
    public void purgeStale() {
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(windowMinutes));
        requests.entrySet().removeIf(entry -> {
            Deque<Instant> timestamps = entry.getValue();
            synchronized (timestamps) {
                while (!timestamps.isEmpty()
                        && timestamps.peekFirst().isBefore(windowStart)) {
                    timestamps.pollFirst();
                }
                return timestamps.isEmpty();
            }
        });
    }
}
