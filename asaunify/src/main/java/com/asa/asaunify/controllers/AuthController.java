package com.asa.asaunify.controllers;


import com.asa.asaunify.dtos.auth.AuthRequest;
import com.asa.asaunify.dtos.auth.AuthResponse;
import com.asa.asaunify.dtos.auth.ForgotPasswordRequest;
import com.asa.asaunify.dtos.auth.ResetPasswordRequest;
import com.asa.asaunify.dtos.auth.VerifyResetCodeRequest;
import com.asa.asaunify.services.AuthService;
import com.asa.asaunify.services.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.Duration;
import java.util.Map;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    // Name of the httpOnly cookie carrying the refresh token.
    private static final String REFRESH_COOKIE = "refreshToken";

    // Secure flag for the refresh cookie — must be true in production (HTTPS).
    // Defaults to false so local http dev works; set to true in prod config.
    @Value("${app.cookie.secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.refresh-expiration-ms:604800000}")
    private long refreshExpirationMs;

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse auth = authService.login(request, httpRequest);

        // Refresh token goes into an httpOnly cookie (never exposed to JS);
        // the access token stays in the body for in-memory use by the SPA.
        ResponseCookie cookie = buildRefreshCookie(
                auth.getRefreshToken(),
                Duration.ofMillis(refreshExpirationMs));
        auth.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(auth);
    }

    // POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {

        String token = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7) : null;
        authService.logout(userDetails.getUsername(), token, httpRequest);

        // Clear the refresh cookie.
        ResponseCookie cleared = buildRefreshCookie("", Duration.ZERO);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .build();
    }

    // POST /api/auth/refresh — refresh token comes from the httpOnly cookie.
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(value = REFRESH_COOKIE, required = false) String refreshToken) {

        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BadCredentialsException("Missing refresh token");
        }

        AuthResponse auth = authService.refreshToken(refreshToken);

        // Rotate the refresh cookie so it keeps a fresh max-age; the token
        // value itself is re-issued by the service where applicable.
        ResponseCookie cookie = buildRefreshCookie(
                auth.getRefreshToken() != null ? auth.getRefreshToken() : refreshToken,
                Duration.ofMillis(refreshExpirationMs));
        auth.setRefreshToken(null);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(auth);
    }

    // ─── Password reset ───────────────────────────────────────

    // POST /api/auth/forgot-password
    // Always 200 with the same body. A different status or message for an
    // unknown address would turn this endpoint into an account enumerator.
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        passwordResetService.requestReset(request, httpRequest);

        return ResponseEntity.ok(Map.of(
                "message", PasswordResetService.GENERIC_REQUEST_RESPONSE));
    }

    // POST /api/auth/verify-reset-code
    // Checks the code without spending it, so the client can show the
    // new-password step. The code is still required at reset time.
    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, String>> verifyResetCode(
            @Valid @RequestBody VerifyResetCodeRequest request) {

        passwordResetService.verifyCode(request);

        return ResponseEntity.ok(Map.of(
                "message", "Verification code accepted."));
    }

    // POST /api/auth/reset-password
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        passwordResetService.resetPassword(request, httpRequest);

        // The reset invalidates existing sessions, so clear the refresh cookie
        // on this client too rather than leaving a token that will be refused.
        ResponseCookie cleared = buildRefreshCookie("", Duration.ZERO);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cleared.toString())
                .body(Map.of("message",
                        "Password updated. You can now log in with your new "
                                + "password."));
    }

    private ResponseCookie buildRefreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
