package com.asa.asaunify.services;

import com.asa.asaunify.config.PasswordResetThrottleService;
import com.asa.asaunify.dtos.auth.ForgotPasswordRequest;
import com.asa.asaunify.dtos.auth.ResetPasswordRequest;
import com.asa.asaunify.dtos.auth.VerifyResetCodeRequest;
import com.asa.asaunify.entity.PasswordResetToken;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.ActionType;
import com.asa.asaunify.logging.AuditService;
import com.asa.asaunify.repos.PasswordResetTokenRepo;
import com.asa.asaunify.repos.UserRepo;
import com.asa.asaunify.util.EmailNormalizer;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Forgot-password / reset-password workflow.
 *
 * Two rules shape most of the code here:
 *
 * 1. No response may reveal whether an address belongs to an account. The
 *    request endpoint always returns the same generic result, and every
 *    verification failure — unknown account, no outstanding request, wrong
 *    code, expired code, already-used code, too many attempts — surfaces as
 *    one identical message. Distinguishing them would let an attacker probe
 *    for valid accounts.
 *
 * 2. The code is a secret of the same class as a password. It is generated
 *    with SecureRandom, stored only as a BCrypt hash, never returned by the
 *    API, and never written to a log or an audit record.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepo userRepository;
    private final PasswordResetTokenRepo tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final AuditService auditService;
    private final PasswordResetThrottleService throttleService;

    // java.util.Random and Math.random() are predictable from observed output.
    // SecureRandom is seeded from the OS entropy pool; one shared instance is
    // thread-safe and avoids per-request re-seeding cost.
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private static final int CODE_BOUND = 1_000_000;  // 000000-999999

    // One message for every rejection — see rule 1 above.
    private static final String GENERIC_FAILURE =
            "Invalid or expired verification code. Please request a new one.";

    // Returned whatever the outcome of a reset request.
    public static final String GENERIC_REQUEST_RESPONSE =
            "If an account exists for this email address, a password reset "
                    + "code has been sent.";

    @Value("${app.password-reset.code-expiry-minutes:10}")
    private long codeExpiryMinutes;

    @Value("${app.password-reset.max-attempts:5}")
    private int maxAttempts;

    // ─── Request a reset code ─────────────────────────────────

    /**
     * Issues a reset code if the address belongs to an active account.
     * Reports nothing back in any case — the caller always responds with
     * GENERIC_REQUEST_RESPONSE.
     */
    @Transactional
    public void requestReset(
            ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        String email = EmailNormalizer.normalize(request.getEmail());
        String clientIp = getClientIp(httpRequest);

        // Throttle on the address and on the source host. Single "&" is
        // deliberate: both keys must record the attempt, so one cannot be
        // used to mask the other.
        boolean allowed = throttleService.tryAcquire("email:" + email)
                & throttleService.tryAcquire("ip:" + clientIp);

        if (!allowed) {
            // Stop silently. Signalling the throttle would tell an attacker
            // their probes are landing on something real.
            log.warn("Password reset throttled for ip {}", clientIp);
            return;
        }

        // Generated and hashed before the account lookup so the expensive
        // BCrypt work happens on every path. A response measurably faster for
        // unknown addresses is an enumeration oracle in its own right.
        String code = generateCode();
        String codeHash = passwordEncoder.encode(code);

        Optional<User> found = userRepository.findByEmail(email);
        if (found.isEmpty()) {
            return;
        }

        User user = found.get();
        if (!user.isActive()) {
            // A deactivated account cannot log in, so it gets no reset path —
            // but the response stays identical.
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        // Supersede anything still outstanding, so exactly one code is live
        // per account and an older code cannot be replayed once a newer one
        // has been issued.
        tokenRepository.consumeAllActiveForUser(user, now);

        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .codeHash(codeHash)
                .expiresAt(now.plusMinutes(codeExpiryMinutes))
                .attemptCount(0)
                .build();
        tokenRepository.save(token);

        emailService.sendPasswordResetCode(
                user.getEmail(), user.getFullName(), code);

        auditService.log(
                user,
                ActionType.PASSWORD_RESET_REQUESTED,
                "USER",
                user.getId().toString(),
                "AUTH",
                httpRequest
        );
    }

    // ─── Verify a code ────────────────────────────────────────

    /**
     * Checks a code without consuming it, so the client can move the user on
     * to the new-password step. A failed check still burns an attempt; the
     * code is only retired by resetPassword.
     */
    // A rejected code must still persist its attempt counter / retirement.
    // Without noRollbackFor, the IllegalArgumentException thrown after the
    // increment rolls the whole transaction back and the limit never applies.
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void verifyCode(VerifyResetCodeRequest request) {
        resolveValidToken(
                EmailNormalizer.normalize(request.getEmail()),
                request.getCode());
    }

    // ─── Complete the reset ───────────────────────────────────

    // See verifyCode: failed attempts must survive the exception.
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public void resetPassword(
            ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String email = EmailNormalizer.normalize(request.getEmail());
        PasswordResetToken token = resolveValidToken(email, request.getCode());
        User user = token.getUser();

        // Same encoder as account creation and login — one hashing mechanism.
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));

        // Truncated to seconds because a JWT iat claim has second resolution;
        // without this, a token issued in the same second as the change would
        // be read as older than it and rejected.
        user.setPasswordChangedAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        userRepository.save(user);

        // Retire the code: single use, and no second reset from this request.
        LocalDateTime now = LocalDateTime.now();
        token.setConsumedAt(now);
        tokenRepository.save(token);

        // Nothing else outstanding may survive a completed reset either.
        tokenRepository.consumeAllActiveForUser(user, now);

        // The holder has proven control of the mailbox; clear the throttle so
        // they are not locked out of their own next attempt.
        throttleService.reset("email:" + email);

        auditService.log(
                user,
                ActionType.PASSWORD_RESET_COMPLETED,
                "USER",
                user.getId().toString(),
                "AUTH",
                httpRequest
        );
    }

    // ─── Helpers ──────────────────────────────────────────────

    /**
     * Resolves the live token for an address and checks the supplied code.
     * Every failure path throws the same exception with the same message.
     */
    private PasswordResetToken resolveValidToken(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_FAILURE));

        if (!user.isActive()) {
            throw new IllegalArgumentException(GENERIC_FAILURE);
        }

        PasswordResetToken token = tokenRepository
                .findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(user)
                .orElseThrow(() -> new IllegalArgumentException(GENERIC_FAILURE));

        if (token.isExpired()) {
            // Retire it so an expired code cannot keep absorbing attempts.
            token.setConsumedAt(LocalDateTime.now());
            tokenRepository.save(token);
            throw new IllegalArgumentException(GENERIC_FAILURE);
        }

        if (token.getAttemptCount() >= maxAttempts) {
            token.setConsumedAt(LocalDateTime.now());
            tokenRepository.save(token);
            throw new IllegalArgumentException(GENERIC_FAILURE);
        }

        if (!passwordEncoder.matches(code, token.getCodeHash())) {
            token.setAttemptCount(token.getAttemptCount() + 1);
            if (token.getAttemptCount() >= maxAttempts) {
                // Exhausted — retire it rather than leave a guessable row.
                token.setConsumedAt(LocalDateTime.now());
            }
            tokenRepository.save(token);
            throw new IllegalArgumentException(GENERIC_FAILURE);
        }

        return token;
    }

    /**
     * Uniform 6-digit code from a cryptographically secure source.
     * nextInt(bound) is unbiased, unlike nextInt() modulo bound.
     */
    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(CODE_BOUND));
    }

    private String getClientIp(HttpServletRequest request) {
        return com.asa.asaunify.util.ClientIp.of(request);
    }

    // ─── Housekeeping ─────────────────────────────────────────

    /**
     * Expired rows hold no useful information and should not accumulate.
     * Runs hourly; the one-day grace period keeps a short forensic window.
     */
    @Scheduled(fixedDelayString = "${app.password-reset.cleanup-interval-ms:3600000}")
    @Transactional
    public void purgeExpiredTokens() {
        int removed = tokenRepository.deleteExpiredBefore(
                LocalDateTime.now().minusDays(1));
        throttleService.purgeStale();
        if (removed > 0) {
            log.debug("Purged {} expired password reset tokens", removed);
        }
    }
}
