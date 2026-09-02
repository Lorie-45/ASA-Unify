package com.asa.asaunify.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

//@Configuration
//@EnableWebSecurity
//@EnableMethodSecurity
//@RequiredArgsConstructor
//public class SecurityConfig {
//
//    private final JwtFilter jwtFilter;
//    private final UserDetailsService userDetailsService;
//
//    @Bean
//    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
//        http
//                .csrf(AbstractHttpConfigurer::disable)
//                .authorizeHttpRequests(auth -> auth
//                        .requestMatchers("/api/auth/**").permitAll()
//                        // WebSocket endpoint
//                        .requestMatchers("/ws/**",
//                                "/swagger-ui/**",
//                                "/swagger-ui.html",
//                                "/v3/api-docs/**",
//                                "/v3/api-docs").permitAll()
//                        // Everything else requires authentication
//                        .anyRequest().authenticated()
//                )
//                // Stateless — no sessions, JWT only
//                .sessionManagement(session -> session
//                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//                )
//                .authenticationProvider(authenticationProvider())
//                // JWT filter runs before Spring's default auth filter
//                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
//
//        return http.build();
//    }

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final UserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource; // ← add this
    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    // API docs are public ONLY when explicitly enabled (dev). Defaults to
    // false so production does not expose the Swagger UI / OpenAPI schema.
    @org.springframework.beans.factory.annotation.Value(
            "${springdoc.api-docs.enabled:false}")
    private boolean apiDocsEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource)) // ← add this
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> {
                        // Let error dispatches render their real status instead
                        // of being re-checked and returned as 401/403.
                        auth.dispatcherTypeMatchers(
                                jakarta.servlet.DispatcherType.ERROR).permitAll();
                        auth.requestMatchers("/api/auth/**").permitAll();
                        auth.requestMatchers("/ws/**").permitAll();
                        // Only expose docs publicly in non-production (flag on).
                        if (apiDocsEnabled) {
                                auth.requestMatchers(
                                        "/swagger-ui/**",
                                        "/swagger-ui.html",
                                        "/v3/api-docs/**",
                                        "/v3/api-docs").permitAll();
                        }
                        auth.anyRequest().authenticated();
                })
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                )
                // Response hardening headers. Spring already sets X-Frame-Options
                // DENY and X-Content-Type-Options nosniff; we add CSP, a strict
                // Referrer-Policy, and HSTS (effective only over HTTPS).
                .headers(headers -> headers
                        // Strict but Swagger-UI compatible: own-origin resources
                        // plus the inline script/style Swagger needs; no framing.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; "
                                        + "script-src 'self' 'unsafe-inline'; "
                                        + "style-src 'self' 'unsafe-inline'; "
                                        + "img-src 'self' data:; "
                                        + "frame-ancestors 'none'; "
                                        + "base-uri 'self'"))
                        .referrerPolicy(rp -> rp.policy(
                                org.springframework.security.web.header.writers
                                        .ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider =
                new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }


    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config
    ) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}