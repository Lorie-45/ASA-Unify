package com.asa.asaunify.controllers;


import com.asa.asaunify.dtos.auth.AuthRequest;
import com.asa.asaunify.dtos.auth.AuthResponse;
import com.asa.asaunify.services.AuthService;
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


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

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
