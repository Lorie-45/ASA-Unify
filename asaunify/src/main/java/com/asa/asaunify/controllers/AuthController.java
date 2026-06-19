package com.asa.asaunify.controllers;


import com.asa.asaunify.dtos.auth.AuthRequest;
import com.asa.asaunify.dtos.auth.AuthResponse;
import com.asa.asaunify.services.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.tags.Tag;


@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthService authService;

    // POST /api/auth/login
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {

        return ResponseEntity.ok(
                authService.login(request, httpRequest)
        );
    }

    // POST /api/auth/logout
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal UserDetails userDetails,
            HttpServletRequest httpRequest) {

        authService.logout(userDetails.getUsername(), httpRequest);
        return ResponseEntity.ok().build();
    }

    // POST /api/auth/refresh
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @RequestHeader("Refresh-Token") String refreshToken) {

        return ResponseEntity.ok(
                authService.refreshToken(refreshToken)
        );
    }
}