package com.asa.asaunify.services;

import com.asa.asaunify.config.PasswordResetThrottleService;
import com.asa.asaunify.dtos.auth.ForgotPasswordRequest;
import com.asa.asaunify.dtos.auth.ResetPasswordRequest;
import com.asa.asaunify.dtos.auth.VerifyResetCodeRequest;
import com.asa.asaunify.entity.PasswordResetToken;
import com.asa.asaunify.entity.User;
import com.asa.asaunify.enums.Role;
import com.asa.asaunify.logging.AuditService;
import com.asa.asaunify.repos.PasswordResetTokenRepo;
import com.asa.asaunify.repos.UserRepo;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Behavioural tests for the password reset rules that actually carry security
 * weight. No Spring context and no database — everything the service touches
 * is mocked, so these run anywhere.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    @Mock private UserRepo userRepository;
    @Mock private PasswordResetTokenRepo tokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private EmailService emailService;
    @Mock private AuditService auditService;
    @Mock private PasswordResetThrottleService throttleService;
    @Mock private HttpServletRequest httpRequest;

    @InjectMocks private PasswordResetService service;

    private User activeUser;

    private static final int MAX_ATTEMPTS = 5;

    @BeforeEach
    void setUp() {
        // @Value fields are populated by Spring at runtime, not by Mockito.
        ReflectionTestUtils.setField(service, "codeExpiryMinutes", 10L);
        ReflectionTestUtils.setField(service, "maxAttempts", MAX_ATTEMPTS);

        activeUser = User.builder()
                .id(UUID.randomUUID())
                .fullName("Test User")
                .email("user@asa.com")
                .passwordHash("old-hash")
                .role(Role.ADMIN)
                .isActive(true)
                .build();

        when(throttleService.tryAcquire(anyString())).thenReturn(true);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed-value");
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
    }

    // ─── Requesting a code ────────────────────────────────────

    @Test
    void unknownEmailIsSilentlyIgnored() {
        when(userRepository.findByEmail("nobody@asa.com"))
                .thenReturn(Optional.empty());

        // Must not throw: an error here would tell a caller the address is
        // unknown, which is exactly the enumeration we are preventing.
        assertDoesNotThrow(() ->
                service.requestReset(forgotRequest("nobody@asa.com"), httpRequest));

        verify(tokenRepository, never()).save(any());
        verify(emailService, never())
                .sendPasswordResetCode(anyString(), anyString(), anyString());
    }

    @Test
    void hashingHappensEvenForUnknownEmailSoTimingDoesNotLeak() {
        when(userRepository.findByEmail("nobody@asa.com"))
                .thenReturn(Optional.empty());

        service.requestReset(forgotRequest("nobody@asa.com"), httpRequest);

        // The expensive BCrypt call must happen on both paths, otherwise the
        // faster response for unknown addresses is itself an oracle.
        verify(passwordEncoder).encode(anyString());
    }

    @Test
    void emailIsNormalizedBeforeLookup() {
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));

        service.requestReset(forgotRequest("  User@ASA.com  "), httpRequest);

        verify(userRepository).findByEmail("user@asa.com");
    }

    @Test
    void deactivatedAccountGetsNoCode() {
        activeUser.setActive(false);
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));

        service.requestReset(forgotRequest("user@asa.com"), httpRequest);

        verify(tokenRepository, never()).save(any());
        verify(emailService, never())
                .sendPasswordResetCode(anyString(), anyString(), anyString());
    }

    @Test
    void throttledRequestDoesNothing() {
        when(throttleService.tryAcquire(anyString())).thenReturn(false);

        assertDoesNotThrow(() ->
                service.requestReset(forgotRequest("user@asa.com"), httpRequest));

        verify(userRepository, never()).findByEmail(anyString());
        verify(tokenRepository, never()).save(any());
    }

    @Test
    void newRequestSupersedesEveryEarlierOne() {
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));

        service.requestReset(forgotRequest("user@asa.com"), httpRequest);

        // Older codes must stop working the moment a newer one is issued.
        verify(tokenRepository).consumeAllActiveForUser(eq(activeUser), any());
        verify(tokenRepository).save(any(PasswordResetToken.class));
    }

    @Test
    void generatedCodeIsSixDigitsAndTheStoredValueIsHashed() {
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));

        service.requestReset(forgotRequest("user@asa.com"), httpRequest);

        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPasswordResetCode(
                eq("user@asa.com"), eq("Test User"), codeCaptor.capture());

        String code = codeCaptor.getValue();
        assertTrue(code.matches("\\d{6}"),
                "expected a 6-digit code, got: " + code);

        ArgumentCaptor<PasswordResetToken> tokenCaptor =
                ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokenRepository).save(tokenCaptor.capture());

        // The plaintext code must never reach storage.
        assertNotEquals(code, tokenCaptor.getValue().getCodeHash());
        assertEquals("hashed-value", tokenCaptor.getValue().getCodeHash());
    }

    @Test
    void generatedCodesVary() {
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));

        // A fixed or trivially predictable code would defeat the whole flow.
        // 20 draws from 10^6 colliding entirely is not a realistic outcome.
        for (int i = 0; i < 20; i++) {
            service.requestReset(forgotRequest("user@asa.com"), httpRequest);
        }

        ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(20))
                .sendPasswordResetCode(anyString(), anyString(), codes.capture());

        assertTrue(codes.getAllValues().stream().distinct().count() > 1,
                "reset codes should not be constant");
    }

    // ─── Verifying a code ─────────────────────────────────────

    @Test
    void wrongCodeBurnsAnAttemptAndIsRejected() {
        PasswordResetToken token = liveToken();
        stubTokenLookup(token);
        when(passwordEncoder.matches("000000", "code-hash")).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyCode(verifyRequest("000000")));

        assertEquals(1, token.getAttemptCount());
        verify(tokenRepository).save(token);
    }

    @Test
    void codeIsRetiredOnceAttemptsAreExhausted() {
        PasswordResetToken token = liveToken();
        token.setAttemptCount(MAX_ATTEMPTS - 1);
        stubTokenLookup(token);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyCode(verifyRequest("000000")));

        assertEquals(MAX_ATTEMPTS, token.getAttemptCount());
        assertNotNull(token.getConsumedAt(),
                "an exhausted token must be retired, not left guessable");
    }

    @Test
    void furtherAttemptsOnAnExhaustedCodeAreRejected() {
        PasswordResetToken token = liveToken();
        token.setAttemptCount(MAX_ATTEMPTS);
        stubTokenLookup(token);

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyCode(verifyRequest("123456")));

        // The correct code is never even checked once the budget is spent.
        verify(passwordEncoder, never()).matches(eq("123456"), anyString());
    }

    @Test
    void expiredCodeIsRejectedAndRetired() {
        PasswordResetToken token = liveToken();
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        stubTokenLookup(token);

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyCode(verifyRequest("123456")));

        assertNotNull(token.getConsumedAt());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }

    @Test
    void noOutstandingRequestIsRejected() {
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));
        when(tokenRepository
                .findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyCode(verifyRequest("123456")));
    }

    @Test
    void everyRejectionCarriesTheSameMessage() {
        // Distinct messages would reveal whether the account exists, whether a
        // reset was requested, and whether the code merely expired.
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.empty());
        String unknownAccount = assertThrows(IllegalArgumentException.class,
                () -> service.verifyCode(verifyRequest("123456"))).getMessage();

        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));
        when(tokenRepository
                .findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.empty());
        String noRequest = assertThrows(IllegalArgumentException.class,
                () -> service.verifyCode(verifyRequest("123456"))).getMessage();

        PasswordResetToken expired = liveToken();
        expired.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        stubTokenLookup(expired);
        String expiredMsg = assertThrows(IllegalArgumentException.class,
                () -> service.verifyCode(verifyRequest("123456"))).getMessage();

        assertEquals(unknownAccount, noRequest);
        assertEquals(unknownAccount, expiredMsg);
    }

    @Test
    void successfulVerificationDoesNotConsumeTheCode() {
        PasswordResetToken token = liveToken();
        stubTokenLookup(token);
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);

        assertDoesNotThrow(() -> service.verifyCode(verifyRequest("123456")));

        assertNull(token.getConsumedAt(),
                "verify is a check; the code is spent by the reset itself");
        assertEquals(0, token.getAttemptCount());
    }

    // ─── Completing the reset ─────────────────────────────────

    @Test
    void resetHashesTheNewPasswordAndRetiresTheCode() {
        PasswordResetToken token = liveToken();
        stubTokenLookup(token);
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);
        when(passwordEncoder.encode("NewPass#1")).thenReturn("new-hash");

        service.resetPassword(resetRequest("123456", "NewPass#1"), httpRequest);

        assertEquals("new-hash", activeUser.getPasswordHash());
        assertNotEquals("NewPass#1", activeUser.getPasswordHash());
        assertNotNull(token.getConsumedAt(), "the code must be single use");
        verify(userRepository).save(activeUser);
    }

    @Test
    void resetStampsPasswordChangedAtSoOldTokensStopWorking() {
        PasswordResetToken token = liveToken();
        stubTokenLookup(token);
        when(passwordEncoder.matches("123456", "code-hash")).thenReturn(true);

        assertNull(activeUser.getPasswordChangedAt());

        service.resetPassword(resetRequest("123456", "NewPass#1"), httpRequest);

        assertNotNull(activeUser.getPasswordChangedAt(),
                "without this, sessions issued before the reset stay valid");
    }

    @Test
    void resetWithAWrongCodeChangesNothing() {
        PasswordResetToken token = liveToken();
        stubTokenLookup(token);
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        assertThrows(IllegalArgumentException.class, () ->
                service.resetPassword(resetRequest("000000", "NewPass#1"),
                        httpRequest));

        assertEquals("old-hash", activeUser.getPasswordHash());
        verify(userRepository, never()).save(any());
    }

    @Test
    void aConsumedCodeCannotBeReplayed() {
        // A consumed token is never returned by the lookup, so a replay looks
        // exactly like "no outstanding request".
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));
        when(tokenRepository
                .findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () ->
                service.resetPassword(resetRequest("123456", "NewPass#1"),
                        httpRequest));

        verify(userRepository, never()).save(any());
    }

    // ─── Fixtures ─────────────────────────────────────────────

    private PasswordResetToken liveToken() {
        return PasswordResetToken.builder()
                .id(UUID.randomUUID())
                .user(activeUser)
                .codeHash("code-hash")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .attemptCount(0)
                .build();
    }

    private void stubTokenLookup(PasswordResetToken token) {
        when(userRepository.findByEmail("user@asa.com"))
                .thenReturn(Optional.of(activeUser));
        when(tokenRepository
                .findFirstByUserAndConsumedAtIsNullOrderByCreatedAtDesc(activeUser))
                .thenReturn(Optional.of(token));
    }

    private ForgotPasswordRequest forgotRequest(String email) {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail(email);
        return request;
    }

    private VerifyResetCodeRequest verifyRequest(String code) {
        VerifyResetCodeRequest request = new VerifyResetCodeRequest();
        request.setEmail("user@asa.com");
        request.setCode(code);
        return request;
    }

    private ResetPasswordRequest resetRequest(String code, String password) {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@asa.com");
        request.setCode(code);
        request.setNewPassword(password);
        return request;
    }
}
