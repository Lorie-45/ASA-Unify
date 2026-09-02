package com.asa.asaunify.config;



import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final TokenRevocationService tokenRevocationService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // No token — skip filter, Spring Security handles the 401
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String token = authHeader.substring(7);
        final String email;

        try {
            email = jwtUtil.extractEmail(token);

            // Only ACCESS tokens may authenticate API calls.
            // A refresh token must never grant access to protected endpoints —
            // it can only be exchanged at /api/auth/refresh.
            if (!jwtUtil.isAccessToken(token)) {
                filterChain.doFilter(request, response);
                return;
            }

            // Reject tokens explicitly revoked (e.g. by logout).
            if (tokenRevocationService.isRevoked(jwtUtil.extractJti(token))) {
                filterChain.doFilter(request, response);
                return;
            }
        } catch (Exception e) {
            // Malformed token — reject
            filterChain.doFilter(request, response);
            return;
        }

        // Only authenticate if not already authenticated
        if (email != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(email);

            if (jwtUtil.isTokenValid(token, userDetails.getUsername())) {
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );
                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }

    // Skip JWT filter for endpoints that don't need an authenticated principal.
    // NOTE: logout is intentionally NOT skipped — it needs the authenticated
    // user in order to revoke that user's tokens.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/api/auth/login")
                || path.equals("/api/auth/refresh")
                || path.startsWith("/ws/");
    }
}