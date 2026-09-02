package com.asa.asaunify.config;



import com.asa.asaunify.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration-ms}")
    private long expirationMs;

    @Value("${jwt.refresh-expiration-ms}")
    private long refreshExpirationMs;

    // ─── Token Generation ────────────────────────────────────

    // Token type claim values — an access token authorizes API calls,
    // a refresh token may ONLY be exchanged at /api/auth/refresh.
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", TYPE_ACCESS);
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId().toString());
        claims.put("fullName", user.getFullName());
        if (user.getDepartment() != null) {
            claims.put("departmentId", user.getDepartment().getId().toString());
        }
        return buildToken(claims, user.getEmail(), expirationMs);
    }

    public String generateRefreshToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("type", TYPE_REFRESH);
        return buildToken(claims, user.getEmail(), refreshExpirationMs);
    }

    private String buildToken(
            Map<String, Object> claims,
            String subject,
            long expiration
    ) {
        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    // ─── Token Validation ────────────────────────────────────

    public boolean isTokenValid(String token, String email) {
        final String extractedEmail = extractEmail(token);
        return extractedEmail.equals(email) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
    }

    // ─── Claims Extraction ───────────────────────────────────

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return extractClaims(token).get("role", String.class);
    }

    public String extractType(String token) {
        return extractClaims(token).get("type", String.class);
    }

    public boolean isAccessToken(String token) {
        return TYPE_ACCESS.equals(extractType(token));
    }

    public boolean isRefreshToken(String token) {
        return TYPE_REFRESH.equals(extractType(token));
    }

    public String extractUserId(String token) {
        return extractClaims(token).get("userId", String.class);
    }

    public Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}